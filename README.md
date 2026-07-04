# FHIR Care Gap Analysis Pipeline

A FHIR care gap analysis pipeline that mirrors a modern enterprise healthcare
analytics platform running entirely on Google Cloud: Synthea-generated FHIR R4
data ingested into a Healthcare API FHIR store, projected into BigQuery,
evaluated against simplified HEDIS-style quality measures, and served through a
Spring Boot API and Angular dashboard.

**Design docs:** [Architecture](docs/ARCHITECTURE.md) ·
[Repository design](docs/REPOSITORY_DESIGN.md)

**Status:** ingestion path complete (service + infrastructure); analytics,
API, and dashboard milestones upcoming.

All clinical data is synthetic (Synthea). No PHI anywhere.

## Local development (no GCP credentials required)

```bash
# 1. Build and test everything
mvn verify

# 2. Start the local FHIR store stand-in (HAPI FHIR on :8090)
docker compose up -d

# 3. Run the ingestion service with the local profile (:8080)
cd services/ingestion-service && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. In another terminal: generate synthetic patients and ingest them
./tools/synthea/generate.sh 25 Massachusetts
./tools/local/ingest-local.sh

# 5. Inspect the store
curl 'http://localhost:8090/fhir/Patient?_summary=count'
```

## Deploying the dev environment to GCP

One-time bootstrap, from `infra/envs/dev`:

```bash
gcloud storage buckets create gs://YOUR_PROJECT-tfstate \
  --location=us-central1 --uniform-bucket-level-access
cp backend.hcl.example backend.hcl   # fill in the bucket name
terraform init -backend-config=backend.hcl
git add -f .terraform.lock.hcl && git commit -m "Pin provider versions"
```

Provision, then push the first real image:

```bash
terraform apply -var project_id=YOUR_PROJECT

# Build and push the ingestion image (from the repo root)
mvn -pl services/ingestion-service jib:build \
  -Dcontainer.registry=$(terraform -chdir=infra/envs/dev output -raw artifact_registry)

# Roll the service onto it (Terraform intentionally ignores image changes)
gcloud run deploy ingestion-service --region us-central1 \
  --image $(terraform -chdir=infra/envs/dev output -raw artifact_registry)/ingestion-service:0.1.0-SNAPSHOT

# End to end: upload bundles and watch them land in the FHIR store
./tools/synthea/upload.sh gs://$(terraform -chdir=infra/envs/dev output -raw ingest_bucket)
gcloud logging read 'resource.labels.service_name=ingestion-service' --limit 20
```

Cost note: everything here scales to zero except the Healthcare API dataset
(cents/month at Synthea scale). The Cloud SQL instance arriving in a later
milestone is the one always-on cost — stop it when not demoing.

## Repository layout

| Path | Contents |
|---|---|
| `services/ingestion-service` | Cloud Run service: GCS event → HAPI parse/validate → idempotent PUT rewrite → FHIR store, with retry/backoff |
| `infra/` | Terraform: `modules/` (fhir-store, ingestion) + `envs/dev` |
| `tools/synthea` | Generate synthetic bundles, upload to the ingest bucket |
| `tools/local` | Drive the local ingestion loop without GCP |
| `docs/` | Architecture and design records |
