output "dataset_id" {
  value = google_bigquery_dataset.fhir_data.dataset_id
}

output "topic_id" {
  description = "Publish here for an on-demand measure run."
  value       = google_pubsub_topic.measure_run_requested.id
}
