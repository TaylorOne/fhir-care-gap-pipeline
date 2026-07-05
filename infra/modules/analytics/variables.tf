variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "dataset_id" {
  description = "BigQuery dataset the FHIR store streams into."
  type        = string
  default     = "fhir_data"
}

variable "service_name" {
  type    = string
  default = "gap-analysis-service"
}

variable "image" {
  description = "Initial container image; CI owns rollouts afterwards."
  type        = string
}

variable "run_schedule" {
  description = "Cron for the nightly measure run (UTC)."
  type        = string
  default     = "0 6 * * *"
}

variable "runtime_service_account_email" {
  description = "Runtime SA of the service; created at the env root."
  type        = string
}

variable "db_jdbc_url" {
  description = "JDBC URL for the operational Postgres (Cloud SQL socket factory + IAM auth)."
  type        = string
}

variable "db_username" {
  description = "Cloud SQL IAM database username of the runtime SA."
  type        = string
}

variable "api_db_user" {
  description = "Database user of care-gap-api; Flyway V3 grants it SELECT."
  type        = string
}
