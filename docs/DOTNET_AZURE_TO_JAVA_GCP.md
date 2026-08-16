# Translation Guide: .NET/Azure to Java/GCP

This guide gives familiar names to the repository's unfamiliar parts. The
comparisons are mental bridges, not claims that the products have identical
features or operating models.

Read [START_HERE.md](START_HERE.md) first for the actual data flow.

## Application-stack translation

| In this repository | Familiar .NET idea | What to notice |
|---|---|---|
| Java 21 | Modern .NET runtime/C# language version | Records, lambdas, streams, and constructor injection should feel familiar; Java has checked exceptions and a different null model. |
| Spring Boot | ASP.NET Core host plus its conventions | It provides DI, HTTP routing, configuration binding, validation, health endpoints, data integrations, and executable packaging. |
| `@SpringBootApplication` | `WebApplication.CreateBuilder` plus startup wiring | Component scanning discovers annotated classes instead of registering every application service explicitly. |
| `@RestController` + `@GetMapping` | `[ApiController]`, `[Route]`, and `[HttpGet]` | Controller methods bind HTTP input and serialize return values as JSON. |
| Constructor injection | Constructor injection | Same concept. A single constructor needs no extra annotation. |
| `@Configuration` + `@Bean` | `IServiceCollection` registrations/factory delegates | Used when a third-party client or explicit construction needs to enter the container. |
| `@ConfigurationProperties` record | Options pattern (`IOptions<T>`) | Binds a namespaced, validated configuration object. |
| `application.yml` | `appsettings.json` | Base configuration with environment-variable placeholders. |
| `application-local.yml` + Spring profile | `appsettings.Development.json` / environment-specific registration | Profiles can change both values and which implementations DI discovers. |
| Maven `pom.xml` | `.csproj`, NuGet references, and MSBuild targets | Declares dependencies, plugins, packaging, and module inheritance in XML. |
| Root Maven reactor | A `.sln` with central package/build defaults | `mvn verify` at the root builds all three service modules. |
| Spring Data JPA | EF Core | Entities plus repositories; JPQL queries object mappings rather than raw table names. |
| `JdbcTemplate` | Dapper or direct ADO.NET with helpers | Explicit SQL and parameter binding; chosen for the analysis service's batch upsert. |
| Flyway migrations | EF Core migrations / DbUp | Ordered SQL migrations run at application startup. |
| JUnit 5 + Mockito | xUnit/NUnit + Moq/NSubstitute | Unit-test structure and test doubles map closely. |
| Testcontainers | Testcontainers for .NET | The same testing idea: real ephemeral dependencies in Docker. |
| Spring Actuator | ASP.NET Core health checks and management endpoints | `/actuator/health/liveness` and `/readiness` support the runtime. |
| Jib | Container image publishing driven by build tooling | Builds a layered Java OCI image without a project Dockerfile or local Docker daemon. |

### How dependency injection appears in the code

Spring stereotypes register classes automatically:

```java
@Service
public class IngestionService {
    public IngestionService(BundleObjectReader reader, FhirStoreClient client, ...) {
        ...
    }
}
```

The rough C# equivalent is an ordinary class plus registrations such as:

```csharp
services.AddScoped<IngestionService>();
services.AddScoped<IBundleObjectReader, GcsBundleObjectReader>();
```

The Java interfaces are the same architectural seams you would use in .NET.
The difference is where selection happens: `@Profile("local")` and
`@Profile("!local")` let component scanning choose the implementation.

### Java syntax you will see often

| Java | Read it as |
|---|---|
| `record IngestionResult(...)` | An immutable C# record-like data carrier |
| `List<T>` | `List<T>` / `IReadOnlyList<T>` depending on usage |
| `stream().map(...).toList()` | LINQ `Select(...).ToList()` |
| `Optional<T>` | An explicit maybe-value; this repo mostly uses nullable values at boundaries |
| `@Override` | An explicit compiler-checked override/interface implementation marker |
| `implements Foo` | Implements an interface |
| `extends JpaRepository<X, ID>` | Inherits a generic repository interface whose implementation Spring generates |
| Text block `""" ... """` | Raw multi-line string |
| Package name | Namespace, normally mirrored by the source directory convention |

One Java-specific habit worth learning early is Maven's source layout:

```text
src/main/java        application source
src/main/resources   YAML, logging config, migrations
src/test/java        tests
src/test/resources   test fixtures
```

## Cloud translation

| GCP service here | Closest Azure starting point | Role in this system |
|---|---|---|
| Cloud Run | Azure Container Apps | Runs request-driven containers, scales to zero, provides revisions and HTTPS. |
| Cloud Storage | Azure Blob Storage | Durable landing area for raw synthetic FHIR bundles. |
| Eventarc | Event Grid | Routes a storage object-finalized event to an authenticated service endpoint. |
| Pub/Sub | Service Bus topic/subscription | Decouples requests for measure runs and pushes them to the analysis service. Pub/Sub's model is not queue-session/workflow oriented in exactly the same way as Service Bus. |
| Cloud Scheduler | A timer-triggered Function, Logic App recurrence, or scheduled job | Publishes the nightly run request. |
| Healthcare API FHIR store | Azure Health Data Services FHIR service | Managed FHIR R4 system of record. |
| BigQuery | Synapse serverless SQL / a cloud data warehouse | Columnar analytical plane where population measure SQL runs. It is not an Azure SQL Database analogue. |
| Cloud SQL for PostgreSQL | Azure Database for PostgreSQL | Relational operational store used by the writer and API. |
| Artifact Registry | Azure Container Registry | Stores deployable OCI images. |
| Cloud Logging/Monitoring | Azure Monitor, Log Analytics, and Application Insights | Collects structured logs and platform metrics. |
| Service account | Managed identity / service principal | Runtime identity receiving IAM roles. In this repo, every workload gets a narrow identity. |
| Application Default Credentials | `DefaultAzureCredential` | A credential chain that uses the attached workload identity in cloud and developer credentials locally when needed. |
| Workload Identity Federation | GitHub OIDC federation to an Entra application/managed identity | Lets Actions deploy without a stored cloud key. |
| GCP project | Usually an Azure subscription plus resource-group concerns | Billing, API enablement, IAM, and resource namespace boundary. The exact governance boundary is different. |

### The most important GCP mental-model shifts

#### Cloud Run is HTTP all the way down

The ingestion and analysis services look like event handlers, but they are
Spring MVC web applications. Eventarc and Pub/Sub authenticate and send HTTP
requests to them. Their response code is part of the message acknowledgement
protocol:

- 2xx means the event is consumed.
- non-2xx means infrastructure should retry.

This is why retry classification lives visibly in the controllers instead of in
a Functions SDK trigger signature.

#### Identity is attached to each revision

Each Cloud Run service executes as a service account. Terraform grants that
account only the resource permissions it needs:

- ingestion can read one bucket and edit one FHIR store;
- analysis can query one BigQuery dataset and connect/login to Cloud SQL;
- the API can connect/login to Cloud SQL;
- the dashboard service account has no data grants.

That is the GCP expression of managed identity plus least-privilege RBAC. The
Google client libraries obtain credentials through ADC, so application code
does not load secrets.

#### APIs must be enabled before resources exist

`google_project_service.required` in `infra/envs/dev/main.tf` enables the GCP
service APIs used by the environment. Think of this as an explicit platform
capability-registration step at project scope. Terraform dependencies ensure
resource creation follows enablement.

#### Regions and locations are resource-specific

The dev composition passes `us-central1` through most modules, including Cloud
Run, Storage, Healthcare API, BigQuery, Scheduler, and Cloud SQL. GCP products
do not all use location in precisely the same way, just as Azure resources vary
between regional and global scope. Data-location compatibility matters for the
FHIR-to-BigQuery stream.

## Terraform: how to read this repository

If you have used Terraform with `azurerm`, the language is unchanged; only the
provider resources and identity model differ.

```text
infra/
├── envs/dev/          one root module: provider, API enablement, wiring, outputs
└── modules/
    ├── fhir-store/    Healthcare dataset/store and BigQuery stream config
    ├── ingestion/     bucket, identities, Cloud Run, Eventarc
    ├── analytics/     BigQuery, Pub/Sub, Scheduler, analysis Cloud Run
    ├── operational-db Cloud SQL database and IAM database users
    ├── serving/       API and dashboard Cloud Run services
    └── cicd/          GitHub OIDC federation and deployer permissions
```

Start with `infra/envs/dev/main.tf`. Treat it like the cloud application's
composition root: it creates shared identities, derives the IAM-authenticated
JDBC URL, and passes outputs from one module into another. Open a child module
only after you know which box in the architecture it implements.

Two ownership rules prevent surprises:

1. Terraform owns service configuration and infrastructure.
2. Deploy workflows own the current application image.

The Cloud Run resources therefore ignore image drift. This is comparable to
keeping infrastructure deployment separate from an application release
pipeline so an infrastructure apply does not redeploy yesterday's image.

## Data technology translation

There are three data representations because they serve different workloads:

| Representation | Why it exists | Do not use it for |
|---|---|---|
| FHIR resources in the Healthcare API | Standards-based clinical system of record, history, and FHIR transaction semantics | Whole-population quality queries |
| Flattened/versioned FHIR tables in BigQuery | Scans, joins, code filtering, time windows, and aggregate analytics | Low-latency per-request dashboard serving |
| Care-gap rows in PostgreSQL | Relational constraints, stable paging/filtering, operational status, and fast API reads | Preserving the complete clinical record |

This resembles a system that uses Azure Health Data Services, exports into an
analytical lake/warehouse, then materializes serving results into PostgreSQL or
Azure SQL. The important pattern is not the vendor product: it is separating
the transactional clinical model, analytical model, and serving model.

FHIR itself supplies another useful boundary. HAPI FHIR parses typed R4
resources and calls a standard FHIR REST endpoint, so the local HAPI server and
Google's managed store differ mainly in base URL and authentication. That is
similar to programming against a standard protocol instead of a cloud-specific
storage SDK everywhere.

## Build and release translation

| Command/file | .NET/Azure-shaped interpretation |
|---|---|
| `mvn verify` | Restore, compile, run unit tests, run configured integration tests, and verify packages for the solution |
| `mvn -pl services/ingestion-service -am ...` | Build a selected project and its required reactor dependencies |
| `mvn ... jib:build` | Build/push the service's container image through the Java build |
| `npm ci` | Deterministic frontend package restore from the lock file |
| `npm run build` | Angular production compilation/bundling |
| `terraform apply` | Provision/update the dev cloud environment |
| `gcloud run deploy ... --image ...` | Roll a container app/service revision to a new image |
| `.github/workflows/ci.yml` | Multi-job validation pipeline for backend, frontend, and IaC |
| `deploy-*.yml` | Path-filtered application release pipelines |

The Maven parent POM is mostly central policy. Each child service POM declares
its own dependencies and opts into Spring Boot/Jib packaging. There is no shared
Java domain assembly equivalent; service boundaries are kept explicit even
when that means a little duplication.

## Where familiar patterns are deliberately different

### JPA in the reader, JDBC in the writer

It might look inconsistent that `care-gap-api` uses JPA while
`gap-analysis-service` uses JDBC. It is a workload decision:

- the API benefits from mapped entities, paging, and composable read queries;
- analysis writes many deterministic rows with one PostgreSQL upsert shape.

The split is analogous to choosing EF Core for query-heavy application code and
Dapper/raw SQL for a high-volume set-based write path.

### The schema belongs to one service

Only `gap-analysis-service` runs Flyway migrations because it is the writer and
owner of the operational model. `care-gap-api` validates its JPA mappings but
does not mutate the schema. Its integration test consumes the writer's
migrations as the shared contract.

### Configuration changes implementations, not just values

The `local` Spring profile replaces cloud adapters:

- a filesystem reader replaces GCS;
- unauthenticated local HAPI replaces the managed FHIR endpoint;
- `LocalMeasureRunner` replaces BigQuery and returns no evaluations.

That last substitution is an honest test boundary, not a BigQuery emulator.
Local code can still exercise the endpoint, migration, and writer plumbing.
The current automated tests cover query binding and orchestration with test
doubles, but do not execute the measure SQL against a real BigQuery dataset; a
GCP-backed validation is therefore necessary when changing measure semantics.

### Angular uses current standalone patterns

There is no traditional `AppModule`. Components declare their own imports, and
the root app uses Angular signals for view state. `ApiService` uses RxJS
observables around `HttpClient`. If your frontend experience is older Angular,
the absence of NgModules is intentional rather than missing scaffolding.

## A .NET-oriented code-reading exercise

Use this small vertical slice to make the stack concrete:

1. Open `services/care-gap-api/.../gap/GapController.java` and identify the
   familiar controller, validation, paging, and DTO pieces.
2. Follow its injected `CareGapRepository`; read JPQL as the JPA equivalent of
   an EF LINQ query over mapped entities.
3. Open `dashboard/src/app/api.service.ts` and find the matching URL and JSON
   interfaces.
4. Open `services/gap-analysis-service/.../V1__care_gap_schema.sql` to see the
   database contract beneath both sides.
5. Finally open `infra/modules/serving/main.tf` to see how identity,
   configuration, networking, and the two serving containers are attached.

That one path crosses UI, HTTP contract, Java DI, ORM, schema ownership, and GCP
deployment without first requiring you to understand FHIR or BigQuery.
