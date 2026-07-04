variable "project_id" {
  description = "Project the ingestion path lives in."
  type        = string
}

variable "region" {
  description = "Region for the bucket, Cloud Run service, and Eventarc trigger."
  type        = string
}

variable "service_name" {
  description = "Cloud Run service name."
  type        = string
  default     = "ingestion-service"
}

variable "bucket_name" {
  description = "Name of the ingest bucket Synthea bundles are uploaded to."
  type        = string
}

variable "image" {
  description = "Initial container image; later rollouts are owned by CI and ignored by Terraform."
  type        = string
}

variable "fhir_store_id" {
  description = "Full FHIR store resource id (projects/…/fhirStores/…) for the IAM binding."
  type        = string
}

variable "fhir_store_url" {
  description = "FHIR R4 base URL injected into the service as FHIR_STORE_URL."
  type        = string
}
