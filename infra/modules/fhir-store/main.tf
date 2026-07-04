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

  # Streaming export: every resource create/update lands in BigQuery within
  # seconds, no export jobs to orchestrate. Appends a row per resource
  # VERSION — measure SQL dedups to the latest version per id.
  dynamic "stream_configs" {
    for_each = var.bigquery_stream_dataset == "" ? [] : [var.bigquery_stream_dataset]
    content {
      bigquery_destination {
        dataset_uri = "bq://${stream_configs.value}"
        schema_config {
          schema_type               = "ANALYTICS_V2"
          recursive_structure_depth = 2
        }
      }
    }
  }
}
