output "fhir_store_url" {
  description = "FHIR R4 base URL of the Healthcare API store (FHIR_STORE_URL for services)."
  value       = module.fhir_store.fhir_store_url
}

output "artifact_registry" {
  description = "Docker registry prefix for `mvn jib:build -Dcontainer.registry=...`."
  value       = "${google_artifact_registry_repository.images.location}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}
