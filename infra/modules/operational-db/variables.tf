variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "instance_name" {
  type    = string
  default = "care-gap-pg"
}

variable "database_name" {
  type    = string
  default = "caregaps"
}

variable "iam_service_accounts" {
  description = "Service account emails to create as Cloud SQL IAM database users."
  type        = list(string)
}
