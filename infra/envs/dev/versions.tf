terraform {
  required_version = ">= 1.9.0"

  # Partial backend configuration: the state bucket name is env-specific and
  # backend blocks cannot interpolate variables. Supply it at init time:
  #   terraform init -backend-config=backend.hcl
  # (see backend.hcl.example)
  backend "gcs" {}

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 6.0.0, < 8.0.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
