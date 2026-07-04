variable "project_id" {
  description = "GCP project that hosts the dev environment."
  type        = string
}

variable "region" {
  description = "Region for all regional resources (Cloud Run, GCS, Healthcare dataset, Artifact Registry)."
  type        = string
  default     = "us-central1"
}

variable "ingestion_image" {
  description = <<-EOT
    Container image for the ingestion service. Defaults to Google's hello
    container so the very first `terraform apply` succeeds before any
    application image has been pushed; after that, image rollouts are owned by
    CI (`mvn jib:build` + `gcloud run deploy`) and Terraform ignores image
    changes on the service.
  EOT
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}
