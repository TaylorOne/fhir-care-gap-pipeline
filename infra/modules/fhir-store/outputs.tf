output "fhir_store_id" {
  description = "Full resource id (projects/…/fhirStores/…), used for IAM bindings."
  value       = google_healthcare_fhir_store.this.id
}

output "fhir_store_url" {
  description = "FHIR R4 REST base URL of the store."
  value       = "https://healthcare.googleapis.com/v1/${google_healthcare_fhir_store.this.id}/fhir"
}
