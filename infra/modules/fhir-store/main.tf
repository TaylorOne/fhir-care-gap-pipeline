resource "google_healthcare_dataset" "this" {
  project  = var.project_id
  name     = var.dataset_id
  location = var.location
}

resource "google_healthcare_fhir_store" "this" {
  name    = var.fhir_store_id
  dataset = google_healthcare_dataset.this.id
  version = "R4"

  # Required by the ingestion design: bundles are rewritten to PUT with
  # client-assigned ids (idempotent replay), so the store must treat an update
  # of a nonexistent resource as a create.
  enable_update_create = true

  # Synthea ships provider/organization bundles separately from patient
  # bundles and Eventarc delivers uploads in arbitrary order, so server-side
  # referential integrity would fail ingests based on upload timing. The
  # analytics plane joins on ids in BigQuery and does not rely on the store
  # enforcing reference validity.
  disable_referential_integrity = true

  # Streaming BigQuery export (stream_configs) is added in the analytics
  # milestone together with the dataset it targets.
}
