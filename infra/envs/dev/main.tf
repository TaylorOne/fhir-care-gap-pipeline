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

  project_id    = var.project_id
  location      = var.region
  dataset_id    = "care-gap-dev"
  fhir_store_id = "clinical-data"

  depends_on = [google_project_service.required]
}
