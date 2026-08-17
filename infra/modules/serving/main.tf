# Public-facing read-only API plus the dashboard that consumes it.
#
# The dashboard is served by Cloud Run (nginx) rather than the originally
# sketched Cloud Storage + CDN: a GCS static site needs an HTTPS load
# balancer (~$18/month — it would dwarf every other cost in the project),
# while Cloud Run scales to zero with TLS included. Recorded as an
# architecture amendment in docs/ARCHITECTURE.md.
#
# CORS needs the dashboard's origin before the dashboard service exists (the
# API and dashboard would otherwise reference each other), and every Cloud
# Run service answers on two valid hostnames (a default hash-based one and a
# deterministic project-number one) so either could show up in a browser.
# A pattern matching the service name sidesteps both problems.
locals {
  dashboard_origin_pattern = "https://${var.dashboard_service_name}-*.run.app"
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

resource "google_cloud_run_v2_service" "api" {
  project             = var.project_id
  name                = var.service_name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = var.runtime_service_account_email

    scaling {
      min_instance_count = 0
      max_instance_count = 3
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
        name  = "DB_JDBC_URL"
        value = var.db_jdbc_url
      }
      env {
        name  = "DB_USERNAME"
        value = var.db_username
      }
      env {
        name  = "DASHBOARD_ORIGIN_PATTERN"
        value = local.dashboard_origin_pattern
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

# Public API by design: read-only, synthetic data, CORS-restricted. A real
# deployment would front this with IAP or an API gateway.
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# --- Dashboard ----------------------------------------------------------------

resource "google_service_account" "dashboard" {
  project      = var.project_id
  account_id   = "${var.dashboard_service_name}-sa"
  display_name = "Runtime identity of ${var.dashboard_service_name} (static content; no grants)"
}

resource "google_cloud_run_v2_service" "dashboard" {
  project             = var.project_id
  name                = var.dashboard_service_name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.dashboard.email

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.dashboard_image

      ports {
        container_port = 8080
      }

      env {
        name  = "API_URL"
        value = google_cloud_run_v2_service.api.uri
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "256Mi"
        }
        cpu_idle = true
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

resource "google_cloud_run_v2_service_iam_member" "dashboard_public" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.dashboard.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
