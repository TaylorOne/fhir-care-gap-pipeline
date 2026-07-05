variable "project_id" {
  type = string
}

variable "github_repository" {
  description = "owner/repo allowed to assume the deployer identity."
  type        = string
}

variable "runtime_service_accounts" {
  description = "Runtime SA emails the deployer may attach to Cloud Run services (actAs)."
  type        = list(string)
}
