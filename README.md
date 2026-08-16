# FHIR Care Gap Analysis Pipeline

A FHIR care gap analysis pipeline that mirrors a modern enterprise healthcare
analytics platform running entirely on Google Cloud: Synthea-generated FHIR R4
data ingested into a Healthcare API FHIR store, projected into BigQuery,
evaluated against simplified HEDIS-style quality measures, and served through a
Spring Boot API and Angular dashboard.

**New to the project?** Start with the [working project map](docs/START_HERE.md)
and the [.NET/Azure to Java/GCP translation guide](docs/DOTNET_AZURE_TO_JAVA_GCP.md).

**Design docs:** [Architecture](docs/ARCHITECTURE.md) ·
[Repository design](docs/REPOSITORY_DESIGN.md)

**Status:** complete — ingestion, analytics (measure SQL + gap analysis),
REST API, Angular dashboard, infrastructure, and CI/CD.

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
(cents/month at Synthea scale) and Cloud SQL — the one always-on cost; stop
the instance when not demoing.

## CI/CD

`ci.yml` builds and tests everything (including Testcontainers integration
tests) on every PR. Pushes to `main` trigger path-filtered deploys: only the
service whose files changed is rebuilt and rolled out, via Workload Identity
Federation — no stored keys. One-time setup after `terraform apply`: set four
GitHub repository variables (Settings → Secrets and variables → Actions →
Variables):

| Variable | Value |
|---|---|
| `GCP_PROJECT` | your project id |
| `GCP_REGION` | `us-central1` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `terraform output cicd_workload_identity_provider` |
| `GCP_DEPLOYER_SA` | `terraform output cicd_deployer_service_account` |

## Repository layout

| Path | Contents |
|---|---|
| `services/ingestion-service` | Cloud Run service: GCS event → HAPI parse/validate → idempotent PUT rewrite → FHIR store, with retry/backoff |
| `services/gap-analysis-service` | Cloud Run service: Pub/Sub → measure SQL on BigQuery → gap upserts into PostgreSQL; owns the schema (Flyway) |
| `services/care-gap-api` | Read-only REST API over the operational store (`/api/gaps`, `/api/gaps/summary`, `/api/measures`, `/api/runs`) |
| `dashboard/` | Angular 20 dashboard (stat tiles, gap table, run history); nginx container with runtime API URL injection |
| `measures/` | Simplified HEDIS-style measure SQL (BigQuery) |
| `infra/` | Terraform: `modules/` (fhir-store, ingestion, analytics, operational-db, serving) + `envs/dev` |
| `tools/synthea` | Generate synthetic bundles, upload to the ingest bucket |
| `tools/local` | Drive the local ingestion loop without GCP |
| `docs/` | Architecture and design records |
