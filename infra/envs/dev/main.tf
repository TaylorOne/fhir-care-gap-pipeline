# Dev environment composition. Modules hold all resource logic; this file only
# wires them together and pins environment-specific naming.

locals {
  required_apis = [
    "healthcare.googleapis.com",
    "run.googleapis.com",
    "eventarc.googleapis.com",
    "artifactregistry.googleapis.com",
    "pubsub.googleapis.com",
    "storage.googleapis.com",
    "iam.googleapis.com",
    "cloudresourcemanager.googleapis.com",
  ]
}

resource "google_project_service" "required" {
  for_each = toset(local.required_apis)
  service  = each.value

  # Disabling an API tears down its resources project-wide; never let a
  # `terraform destroy` of this stack do that.
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "images" {
  repository_id = "fhir-pipeline"
  description   = "Container images for the FHIR care gap pipeline services"
  format        = "DOCKER"
  location      = var.region

  depends_on = [google_project_service.required]
}

module "fhir_store" {
  source = "../../modules/fhir-store"

  project_id              = var.project_id
  location                = var.region
  dataset_id              = "care-gap-dev"
  fhir_store_id           = "clinical-data"
  bigquery_stream_dataset = "${var.project_id}.${module.analytics.dataset_id}"

  depends_on = [google_project_service.required]
}

# Created here rather than inside a module: it is an input to both the
# analytics module (Cloud Run identity, BQ grants) and the operational-db
# module (IAM database user).
resource "google_service_account" "gap_analysis_runtime" {
  account_id   = "gap-analysis-sa"
  display_name = "Runtime identity of gap-analysis-service"
}

module "operational_db" {
  source = "../../modules/operational-db"

  project_id           = var.project_id
  region               = var.region
  iam_service_accounts = [google_service_account.gap_analysis_runtime.email]

  depends_on = [google_project_service.required]
}

module "analytics" {
  source = "../../modules/analytics"

  project_id                    = var.project_id
  region                        = var.region
  image                         = var.gap_analysis_image
  runtime_service_account_email = google_service_account.gap_analysis_runtime.email
  db_username                   = trimsuffix(google_service_account.gap_analysis_runtime.email, ".gserviceaccount.com")
  db_jdbc_url = join("", [
    "jdbc:postgresql:///${module.operational_db.database_name}",
    "?cloudSqlInstance=${module.operational_db.connection_name}",
    "&socketFactory=com.google.cloud.sql.postgres.SocketFactory",
    "&enableIamAuth=true",
  ])

  depends_on = [google_project_service.required]
}

module "ingestion" {
  source = "../../modules/ingestion"

  project_id     = var.project_id
  region         = var.region
  bucket_name    = "${var.project_id}-fhir-ingest"
  image          = var.ingestion_image
  fhir_store_id  = module.fhir_store.fhir_store_id
  fhir_store_url = module.fhir_store.fhir_store_url

  depends_on = [google_project_service.required]
}
