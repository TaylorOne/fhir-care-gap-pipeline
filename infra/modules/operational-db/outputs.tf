output "connection_name" {
  description = "project:region:instance — used in the JDBC URL's cloudSqlInstance."
  value       = google_sql_database_instance.this.connection_name
}

output "database_name" {
  value = google_sql_database.caregaps.name
}
