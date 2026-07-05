output "fhir_store_url" {
  description = "FHIR R4 base URL of the Healthcare API store (FHIR_STORE_URL for services)."
  value       = module.fhir_store.fhir_store_url
}

output "ingest_bucket" {
  description = "Bucket Synthea bundles are uploaded to (tools/synthea/upload.sh target)."
  value       = module.ingestion.ingest_bucket
}

output "api_url" {
  description = "Public base URL of the care-gap API."
  value       = module.serving.api_url
}

output "dashboard_url" {
  description = "Public URL of the dashboard."
  value       = module.serving.dashboard_url
}

output "artifact_registry" {
  description = "Docker registry prefix for `mvn jib:build -Dcontainer.registry=...`."
  value       = "${google_artifact_registry_repository.images.location}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}
