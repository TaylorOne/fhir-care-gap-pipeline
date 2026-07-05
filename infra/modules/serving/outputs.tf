output "api_url" {
  description = "Public base URL of the care-gap API."
  value       = google_cloud_run_v2_service.api.uri
}

output "dashboard_url" {
  description = "Public URL of the dashboard."
  value       = google_cloud_run_v2_service.dashboard.uri
}

output "dashboard_service_account" {
  description = "Dashboard runtime SA (deployer needs actAs on it)."
  value       = google_service_account.dashboard.email
}
