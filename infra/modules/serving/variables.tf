variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "service_name" {
  type    = string
  default = "care-gap-api"
}

variable "image" {
  description = "Initial container image; CI owns rollouts afterwards."
  type        = string
}

variable "runtime_service_account_email" {
  description = "Runtime SA of the API; created at the env root (also a Cloud SQL IAM user)."
  type        = string
}

variable "db_jdbc_url" {
  type = string
}

variable "db_username" {
  type = string
}

variable "dashboard_service_name" {
  type    = string
  default = "care-gap-dashboard"
}

variable "dashboard_image" {
  description = "Dashboard container image; same placeholder-then-CI story as the API image."
  type        = string
}
