output "api_url" {
  description = "Public base URL of the care-gap API."
  value       = google_cloud_run_v2_service.api.uri
}
