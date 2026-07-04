output "ingest_bucket" {
  description = "Bucket to upload Synthea bundles to."
  value       = google_storage_bucket.ingest.name
}

output "service_name" {
  description = "Cloud Run service name (for gcloud run deploy in CI)."
  value       = google_cloud_run_v2_service.ingestion.name
}

output "runtime_service_account" {
  description = "Runtime SA email, for granting future least-privilege roles."
  value       = google_service_account.runtime.email
}
