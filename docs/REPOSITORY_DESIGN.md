# Repository Design — FHIR Care Gap Analysis Pipeline

**Status:** Proposed (Phase 2 — awaiting approval)
**Date:** 2026-07-04
**Depends on:** [ARCHITECTURE.md](./ARCHITECTURE.md) (approved)

---

## 1. Monorepo

One repository holds the three Java services, the Angular dashboard, Terraform,
measure SQL, and tooling.

**Why:** the components version together (a measure SQL change often pairs with
an API or schema change), a portfolio reviewer should see the whole system in one
place, and cross-cutting refactors stay atomic. Polyrepo buys independent release
cadence and access control — neither matters for a single-team portfolio project,
and both cost real friction (submodules, cross-repo PRs, drift).

The one boundary we *do* keep hard: the Angular app and Terraform live in the
monorepo but have completely independent toolchains. Nothing in the Maven build
touches them; CI wires them together, not the build tools.

## 2. Directory Layout

```
fhir-care-gap-pipeline/
├── pom.xml                         # Maven parent (aggregator + dependencyManagement)
├── services/
│   ├── ingestion-service/          # Cloud Run: GCS event → HAPI parse → FHIR store
│   │   ├── pom.xml
│   │   └── src/main/java, src/main/resources, src/test/...
│   ├── gap-analysis-service/       # Cloud Run: Pub/Sub → BigQuery SQL → Postgres upsert
│   │   ├── pom.xml
│   │   └── src/...
│   └── care-gap-api/               # Cloud Run: REST API over Postgres
│       ├── pom.xml
│       └── src/... (+ db/migration Flyway scripts — this service owns the schema)
├── dashboard/                      # Angular app (own package.json, untouched by Maven)
├── measures/                       # Versioned measure SQL templates
│   ├── cdc-a1c.sql
│   ├── bcs-mammography.sql
│   └── col-colorectal.sql
├── infra/                          # Terraform
│   ├── modules/
│   │   ├── fhir-store/             # dataset, FHIR store, streaming BQ export config
│   │   ├── ingestion/              # bucket, Eventarc trigger, Cloud Run, SA
│   │   ├── analytics/              # BQ dataset, _latest views, Pub/Sub, Scheduler
│   │   ├── operational-db/         # Cloud SQL instance + database
│   │   └── serving/                # care-gap-api Cloud Run, dashboard bucket + CDN
│   └── envs/
│       └── dev/                    # the only env for now; layout leaves room for more
├── tools/
│   └── synthea/                    # generate-and-upload scripts, config
├── docs/
│   ├── ARCHITECTURE.md
│   └── REPOSITORY_DESIGN.md
└── .github/
    └── workflows/                  # ci.yml, deploy-*.yml
```

## 3. Maven Module Strategy

**Parent POM = `dependencyManagement` + plugin management + aggregator. Three
service modules. No shared library module — deliberately.**

The obvious temptation is a `libs/common` module. I'm rejecting it at the start
because the services genuinely share almost nothing:

| Service | Talks to | Distinct stack |
|---|---|---|
| ingestion-service | GCS, Healthcare API | HAPI FHIR, Google API client |
| gap-analysis-service | BigQuery, Postgres | BigQuery client, **JdbcTemplate** |
| care-gap-api | Postgres | Spring Data JPA, Flyway |

The only plausible shared code is the care-gap table model between
`gap-analysis-service` (writer) and `care-gap-api` (reader). Sharing JPA entities
across services is a classic coupling mistake — a reader-driven entity change
silently alters writer behavior. Instead:

- **The schema is the contract.** `care-gap-api` owns it via Flyway migrations.
  *(Amended during milestone 3: ownership moved to `gap-analysis-service` — the
  writer ships first and defines what it produces; the API will validate
  against the schema rather than migrate it.)*
- **The writer uses plain `JdbcTemplate`** with set-based
  `INSERT … ON CONFLICT … DO UPDATE` upserts — the right tool for bulk writes
  anyway; row-at-a-time JPA would be the wrong instrument there.
- **The reader uses Spring Data JPA**, which is the right tool for paginated,
  filtered REST queries.

If a third consumer of the schema ever appears, we extract a module then, with
evidence. Common-library-first is how portfolio projects grow enterprise scar
tissue without the enterprise.

The parent POM pins: Java 21, Spring Boot 3.x BOM, HAPI FHIR BOM, Google Cloud
libraries BOM, Testcontainers BOM, and shared plugin config (surefire, failsafe,
jib, spotless). Services declare dependencies versionlessly.

## 4. Package Layout (per service)

Base package `io.github.taylorone.fhirpipeline.<service>` (matches the GitHub
org; a one-line rename if you prefer another groupId). Layout is
package-by-feature with thin technical seams — three services this small do not
warrant full hexagonal ceremony, but each external system gets an interface so
tests can fake it:

```
io.github.taylorone.fhirpipeline.ingestion/
├── IngestionApplication.java
├── config/            # @ConfigurationProperties (bucket, FHIR store path), FhirContext bean
├── event/             # CloudEvents HTTP endpoint (Eventarc target), event model
├── storage/           # BundleObjectReader (GCS download)
├── fhir/              # BundleParser (HAPI), FhirStoreClient (executeBundle + retry/backoff)
└── ingest/            # IngestionService — orchestrates the above; the unit-test target
```

`gap-analysis-service`: `event/` (Pub/Sub push endpoint), `measure/` (loads SQL
templates from `measures/`, packaged as classpath resources at build time),
`bigquery/`, `gaps/` (upsert writer + run bookkeeping).

`care-gap-api`: `gap/`, `measure/`, `summary/` feature packages, each with
controller/service/repository; `config/` for CORS + error handling
(`@ControllerAdvice`, RFC 7807 problem details).

## 5. Build & Images

**Jib** (Maven plugin) builds container images — no Dockerfile, no Docker daemon,
reproducible layered images (deps cached separately from app classes), straight
to Artifact Registry. Base image: `eclipse-temurin:21-jre` distroless-style
variant. Trade-off: Dockerfiles are more universally legible; Jib is what
Java-on-GCP teams actually use and removes an entire class of "works in my
Docker" drift. One shared Jib config block in the parent POM.

## 6. Deployment Strategy

- **Infra:** Terraform, applied manually from `infra/envs/dev` for now (state in
  a GCS backend bucket). CI runs `terraform fmt -check` + `validate` + `plan` on
  PRs; auto-apply is deliberately out of scope — reviewing plans by hand is the
  honest posture for a one-person project with a stateful FHIR store.
- **Services:** GitHub Actions with **Workload Identity Federation** (zero
  long-lived keys — the same principle as ADC at runtime). `deploy-<service>.yml`
  triggers on push to `main` with a path filter (`services/ingestion-service/**`
  → only that service deploys): `mvn verify` → `jib:build` → `gcloud run deploy`
  with the new image digest.
- **Dashboard:** `deploy-dashboard.yml`: `ng build` → `gsutil rsync` to the
  static bucket → CDN cache invalidation.
- **Environments:** single `dev` environment. The Terraform layout
  (`envs/<name>` + shared modules) makes a `prod` copy a directory, not a
  refactor — we demonstrate the pattern without paying for two environments.

Branching: trunk-based. Short-lived feature branches → PR → `main`; `ci.yml`
runs build + tests on every PR; deploys only from `main`.

## 7. Local Development Workflow

The guiding rule: **everything with a faithful local substitute runs locally;
everything without one is faked at an interface seam and integration-tested
against the real dev project.**

| Dependency | Local strategy |
|---|---|
| PostgreSQL | `docker-compose.yml` at repo root; Testcontainers in integration tests |
| FHIR store | **HAPI FHIR JPA server container** as a local stand-in — it speaks the same FHIR R4 REST API, so `FhirStoreClient` pointed at `localhost:8080/fhir` exercises real bundle semantics. (No official Healthcare API emulator exists.) |
| GCS events | Ingestion's Eventarc endpoint is plain HTTP+CloudEvents; a `curl`/script posts a synthetic event referencing a local file path via a `local` profile storage reader |
| BigQuery | **No credible emulator** — honest gap. `BigQueryMeasureRunner` sits behind an interface; unit tests fake it, and a small failsafe integration-test suite (`-Pintegration-gcp`) runs the real measure SQL against the dev dataset with Synthea data. Measure SQL correctness is validated where it actually runs. |
| Pub/Sub | Push subscription = HTTP POST; local `curl` with a Pub/Sub-shaped envelope. (The official Pub/Sub emulator exists if we later need pull semantics.) |

Day-one loop: `docker compose up` (Postgres + HAPI stand-in) → `mvn
spring-boot:run` per service with the `local` profile → `tools/synthea/generate.sh`
to produce bundles → script posts them through ingestion → trigger a measure run
with `curl` → hit `care-gap-api` → `ng serve` proxies to it.

Spring profiles: `local` (compose endpoints, no GCP creds needed except for the
optional BigQuery integration tests) and `gcp` (ADC, real endpoints). No
`dev`/`staging`/`prod` profile proliferation — environment differences live in
environment variables, not code.

## 8. Testing Strategy (summary)

- **Unit:** JUnit 5 + Mockito; orchestration classes (`IngestionService`,
  measure runner) tested against faked seams. HAPI parsing tested with real
  Synthea sample bundles checked into `src/test/resources`.
- **Integration:** Testcontainers Postgres for repository/Flyway tests;
  Testcontainers HAPI FHIR for `FhirStoreClient` (real HTTP, real FHIR
  semantics); WireMock for retry/backoff behavior (429/503 sequences).
- **GCP-bound:** opt-in failsafe profile for BigQuery measure SQL, run in CI
  against the dev project via WIF.

## 9. Implementation Order (Phase 3 preview)

1. Parent POM + `ingestion-service` (with tests + Jib) ← **next milestone**
2. Terraform: FHIR store + ingestion path; first end-to-end Synthea → FHIR store run
3. Analytics: BigQuery export config, `_latest` views, measure SQL, `gap-analysis-service`
4. `care-gap-api` + Flyway schema
5. Dashboard
6. CI/CD workflows hardening + docs polish

Each step lands as a reviewed PR-sized unit; we pause after each per the
development approach.
