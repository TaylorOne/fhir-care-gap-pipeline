# Analytics plane: BigQuery dataset fed by FHIR store streaming export, and
# the Pub/Sub-triggered gap-analysis Cloud Run service with its scheduler.

resource "google_bigquery_dataset" "fhir_data" {
  project    = var.project_id
  dataset_id = var.dataset_id
  location   = var.region

  # Dev: allow destroy even with exported data present.
  delete_contents_on_destroy = true
}

# The Healthcare API writes the streaming export as its service agent. The
# agent (service-<num>@gcp-sa-healthcare...) is created when the Healthcare
# API is enabled; constructing the email from the project number avoids the
# google-beta-only google_project_service_identity resource.
data "google_project" "this" {
  project_id = var.project_id
}

locals {
  healthcare_agent = "service-${data.google_project.this.number}@gcp-sa-healthcare.iam.gserviceaccount.com"
}

resource "google_bigquery_dataset_iam_member" "healthcare_agent_writes" {
  project    = var.project_id
  dataset_id = google_bigquery_dataset.fhir_data.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${local.healthcare_agent}"
}

resource "google_project_iam_member" "healthcare_agent_runs_jobs" {
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${local.healthcare_agent}"
}

# --- Measure-run eventing -----------------------------------------------------

resource "google_pubsub_topic" "measure_run_requested" {
  project = var.project_id
  name    = "measure-run-requested"
}

resource "google_cloud_scheduler_job" "nightly_run" {
  project  = var.project_id
  region   = var.region
  name     = "nightly-measure-run"
  schedule = var.run_schedule

  pubsub_target {
    topic_name = google_pubsub_topic.measure_run_requested.id
    data       = base64encode("{}") # empty payload = run as of today
  }
}

# --- Gap-analysis service -------------------------------------------------------

# The runtime SA is created at the env root (it is also an input to the
# operational-db module for the IAM database user; creating it here would
# make the two modules mutually dependent).

resource "google_project_iam_member" "runtime_runs_bq_jobs" {
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${var.runtime_service_account_email}"
}

resource "google_bigquery_dataset_iam_member" "runtime_reads_fhir_data" {
  project    = var.project_id
  dataset_id = google_bigquery_dataset.fhir_data.dataset_id
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${var.runtime_service_account_email}"
}

resource "google_project_iam_member" "runtime_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${var.runtime_service_account_email}"
}

resource "google_project_iam_member" "runtime_cloudsql_iam_login" {
  project = var.project_id
  role    = "roles/cloudsql.instanceUser"
  member  = "serviceAccount:${var.runtime_service_account_email}"
}

resource "google_cloud_run_v2_service" "gap_analysis" {
  project             = var.project_id
  name                = var.service_name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  deletion_protection = false

  template {
    service_account = var.runtime_service_account_email

    scaling {
      min_instance_count = 0
      max_instance_count = 1 # measure runs are whole-population; never run two at once
    }

    containers {
      image = var.image

      ports {
        container_port = 8080
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "gcp"
      }
      env {
        name  = "BQ_PROJECT"
        value = var.project_id
      }
      env {
        name  = "BQ_DATASET"
        value = google_bigquery_dataset.fhir_data.dataset_id
      }
      env {
        name  = "DB_JDBC_URL"
        value = var.db_jdbc_url
      }
      env {
        name  = "DB_USERNAME"
        value = var.db_username
      }
      env {
        name  = "API_DB_USER"
        value = var.api_db_user
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle = true
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

# --- Push subscription --------------------------------------------------------

resource "google_service_account" "pusher" {
  project      = var.project_id
  account_id   = "gap-analysis-pusher"
  display_name = "Pub/Sub push identity for ${var.service_name}"
}

resource "google_cloud_run_v2_service_iam_member" "pusher_invokes_service" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.gap_analysis.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.pusher.email}"
}

resource "google_pubsub_subscription" "measure_run_push" {
  project = var.project_id
  name    = "measure-run-requested-push"
  topic   = google_pubsub_topic.measure_run_requested.id

  # A whole-population run can take minutes; give the endpoint the maximum.
  ack_deadline_seconds = 600

  push_config {
    push_endpoint = google_cloud_run_v2_service.gap_analysis.uri

    oidc_token {
      service_account_email = google_service_account.pusher.email
    }
  }

  retry_policy {
    minimum_backoff = "60s"
    maximum_backoff = "600s"
  }

  depends_on = [google_cloud_run_v2_service_iam_member.pusher_invokes_service]
}
