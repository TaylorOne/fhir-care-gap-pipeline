# Operational PostgreSQL for care-gap records. Smallest possible footprint —
# this is the one always-on cost in the architecture (~$10-30/month); stop the
# instance when not demoing. Access is exclusively Cloud SQL IAM auth through
# the Java connector: no database passwords exist anywhere in the system.

resource "google_sql_database_instance" "this" {
  project          = var.project_id
  name             = var.instance_name
  region           = var.region
  database_version = "POSTGRES_16"

  # Dev posture: rebuildable from a measure run, so make teardown easy.
  deletion_protection = false

  settings {
    tier              = "db-f1-micro"
    edition           = "ENTERPRISE"
    availability_type = "ZONAL"
    disk_type         = "PD_HDD"
    disk_size         = 10

    database_flags {
      name  = "cloudsql.iam_authentication"
      value = "on"
    }

    backup_configuration {
      enabled = false # synthetic data, rebuildable
    }

    ip_configuration {
      # Public IP with no authorized networks: unreachable directly; the Java
      # connector tunnels via the Cloud SQL Admin API with IAM auth.
      ipv4_enabled = true
    }
  }
}

resource "google_sql_database" "caregaps" {
  project  = var.project_id
  name     = var.database_name
  instance = google_sql_database_instance.this.name
}

resource "google_sql_user" "iam_users" {
  for_each = toset(var.iam_service_accounts)

  project  = var.project_id
  instance = google_sql_database_instance.this.name
  type     = "CLOUD_IAM_SERVICE_ACCOUNT"
  # Cloud SQL IAM usernames for SAs drop the .gserviceaccount.com suffix.
  name = trimsuffix(each.value, ".gserviceaccount.com")
}
