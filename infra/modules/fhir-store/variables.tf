variable "project_id" {
  description = "Project the Healthcare dataset lives in."
  type        = string
}

variable "location" {
  description = "Healthcare API dataset location (e.g. us-central1)."
  type        = string
}

variable "dataset_id" {
  description = "Healthcare dataset name."
  type        = string
}

variable "fhir_store_id" {
  description = "FHIR store name within the dataset."
  type        = string
}

variable "bigquery_stream_dataset" {
  description = "BigQuery dataset (\"project.dataset\") to stream every resource version into; empty disables streaming export."
  type        = string
  default     = ""
}
