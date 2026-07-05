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

variable "dashboard_origin" {
  description = "Origin allowed by CORS; the dashboard's URL."
  type        = string
  default     = "http://localhost:4200"
}
