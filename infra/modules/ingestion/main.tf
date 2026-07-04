# The GCS → Eventarc → Cloud Run → FHIR store ingestion path.
#
# IAM design: two service accounts with least privilege.
#  - runtime SA: what the service can DO (read the ingest bucket, write the
#    one FHIR store — not project-wide Healthcare access).
#  - trigger SA: what Eventarc can do (invoke this one Cloud Run service).

resource "google_storage_bucket" "ingest" {
  project  = var.project_id
  name     = var.bucket_name
  location = var.region

  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  # Dev convenience: allow `terraform destroy` while bundles are present.
  # A production environment would keep raw input immutable instead.
  force_destroy = true
}

# --- Runtime identity ---------------------------------------------------------

resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = "${var.service_name}-sa"
  display_name = "Runtime identity of ${var.service_name}"
}

resource "google_storage_bucket_iam_member" "runtime_reads_bundles" {
  bucket = google_storage_bucket.ingest.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_healthcare_fhir_store_iam_member" "runtime_writes_fhir" {
  fhir_store_id = var.fhir_store_id
  role          = "roles/healthcare.fhirResourceEditor"
  member        = "serviceAccount:${google_service_account.runtime.email}"
}

# --- Cloud Run service --------------------------------------------------------

resource "google_cloud_run_v2_service" "ingestion" {
  project  = var.project_id
  name     = var.service_name
  location = var.region

  # Only Eventarc (and other in-project internal callers) can reach it.
  ingress             = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  deletion_protection = false

  template {
    service_account = google_service_account.runtime.email

    scaling {
      min_instance_count = 0 # scale to zero: ingestion is bursty
      max_instance_count = 3 # cap concurrent pressure on the FHIR store
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
        name  = "FHIR_STORE_URL"
        value = var.fhir_store_url
      }
      env {
        name  = "INGEST_BUCKET"
        value = google_storage_bucket.ingest.name
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
    # CI owns image rollouts (jib:build + gcloud run deploy); Terraform must
    # not roll the service back to var.image on the next apply.
    ignore_changes = [template[0].containers[0].image]
  }
}

# --- Eventarc trigger ---------------------------------------------------------

resource "google_service_account" "trigger" {
  project      = var.project_id
  account_id   = "${var.service_name}-trigger"
  display_name = "Eventarc trigger identity for ${var.service_name}"
}

resource "google_cloud_run_v2_service_iam_member" "trigger_invokes_service" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.ingestion.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.trigger.email}"
}

resource "google_project_iam_member" "trigger_receives_events" {
  project = var.project_id
  role    = "roles/eventarc.eventReceiver"
  member  = "serviceAccount:${google_service_account.trigger.email}"
}

# GCS publishes object events through Pub/Sub; its service agent needs
# permission to publish or the trigger silently never fires.
data "google_storage_project_service_account" "gcs" {
  project = var.project_id
}

resource "google_project_iam_member" "gcs_publishes_events" {
  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${data.google_storage_project_service_account.gcs.email_address}"
}

resource "google_eventarc_trigger" "object_finalized" {
  project         = var.project_id
  name            = "${var.service_name}-object-finalized"
  location        = var.region
  service_account = google_service_account.trigger.email

  matching_criteria {
    attribute = "type"
    value     = "google.cloud.storage.object.v1.finalized"
  }
  matching_criteria {
    attribute = "bucket"
    value     = google_storage_bucket.ingest.name
  }

  destination {
    cloud_run_service {
      service = google_cloud_run_v2_service.ingestion.name
      region  = var.region
      path    = "/"
    }
  }

  depends_on = [
    google_cloud_run_v2_service_iam_member.trigger_invokes_service,
    google_project_iam_member.trigger_receives_events,
    google_project_iam_member.gcs_publishes_events,
  ]
}
