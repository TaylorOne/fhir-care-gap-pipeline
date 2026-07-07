# Roadmap — Growth Workstreams

The v1 pipeline is complete (see [ARCHITECTURE.md](./ARCHITECTURE.md)). These
workstreams extend it deliberately across more of the GCP platform. Each is
scoped to be independently completable and ends with a short decision-record
note in `docs/` — additions that contradict a v1 decision get an explicit
amendment, the same way the dashboard-hosting and schema-ownership changes
were recorded.

Suggested order: W0 → W1 → W2 → W3 (staged) → W4 → W5. W6 is optional.

---

## W0 — First real deployment + operations baseline

*The prerequisite for everything below.*

- [ ] Bootstrap state bucket, `terraform init/apply` on a fresh project;
      commit the generated `.terraform.lock.hcl`.
- [ ] Jib-push all three service images + dashboard image; first end-to-end
      run: Synthea → GCS → FHIR store → BigQuery → gaps → dashboard.
- [ ] Fix whatever the ANALYTICS_V2 export schema breaks in `measures/*.sql`
      (expected: column shapes/casts). Record the actual schema learnings in
      the measure SQL comments.
- [ ] Verify DetectedIssue write-back resources in the FHIR store.
- [ ] Add the missing operational pieces as they bite: Eventarc dead-letter
      topic + alert policy, uptime checks, a `docs/RUNBOOK.md` capturing every
      issue hit and how it was diagnosed (logs explorer queries, gcloud
      commands used).

**New surface:** console + gcloud fluency, real troubleshooting.

## W1 — Network hardening (VPC)

v1 runs everything on the default network with Cloud SQL on a public IP
(unreachable without IAM, but still). Replace with a real network design:

- [ ] Custom VPC + subnet in Terraform; delete reliance on `default`.
- [ ] Cloud SQL on **private IP** (private services access peering); remove
      the public IP.
- [ ] Cloud Run → Cloud SQL via **direct VPC egress** (no serverless VPC
      connector — cheaper, newer; note the trade-off).
- [ ] Firewall rules scoped to what actually talks (document each rule).
- [ ] Decision record: what changed vs v1 and why the default was acceptable
      for v1 but not beyond.

**New surface:** VPC design, private services access, egress paths.

## W2 — Serverless preprocessing (Cloud Functions)

A genuine gap in the ingest path: Synthea batches arrive as one archive, not
per-patient files.

- [ ] Cloud Functions (2nd gen) function, GCS-triggered: a `.zip`/`.tar.gz`
      landing in an `uploads/` prefix is unpacked into individual bundle
      JSONs in the watched prefix (which then flow through Eventarc →
      ingestion as usual). Reject anything that is not JSON inside.
- [ ] Terraform module + CI deploy workflow for the function.
- [ ] Decision record: when a function beats a Cloud Run service (this case)
      and when it doesn't (the v1 ingestion service — revisit the original
      Cloud Run vs Functions analysis with hands-on evidence).

**New surface:** Cloud Functions 2nd gen, event filters, function CI.

## W3 — ML track (staged): gap-closure propensity

Predict which open gaps are unlikely to close without outreach, and surface
an outreach-priority score in the API/dashboard. Synthetic data means the
model learns Synthea's generator dynamics — document that loudly; the value
is the MLOps mechanics, not the clinical model.

**Stage 1 — features + baseline (BigQuery only)**
- [ ] Feature engineering SQL over the FHIR export: age, condition burden,
      encounter frequency/recency, historical gap-closure behavior per
      patient (needs a small gap-history table first — worth adding anyway
      for dashboard trends).
- [ ] Baseline model with **BigQuery ML** (logistic regression): train,
      evaluate (AUC), predict — all in SQL. Cheap, fast to iterate.

**Stage 2 — Vertex AI custom training + registry**
- [ ] Port the model to a containerized training job (scikit-learn/XGBoost),
      run as a **Vertex AI custom training job** reading the features table.
- [ ] Register versions in **Vertex AI Model Registry** with eval metrics;
      write the promotion rule down (what makes a version deployable).
- [ ] **Batch predictions** written to BigQuery, then upserted into Postgres
      (`care_gap.outreach_score`) by the gap-analysis run.

**Stage 3 — pipeline + surfacing**
- [ ] Orchestrate train→eval→register as a **Vertex AI Pipeline** (KFP),
      triggered on demand; document the DAG.
- [ ] API exposes the score; dashboard sorts open gaps by outreach priority.
- [ ] Decision record: BQML vs custom training trade-off, observed costs.

**New surface:** feature engineering, Vertex AI training/registry/batch
prediction/pipelines, model versioning, ML pipeline troubleshooting.

## W4 — GKE as an alternate runtime (time-boxed)

Cloud Run remains the production answer (v1 analysis stands). Run the
care-gap-api on **GKE Autopilot** as a comparative exercise:

- [ ] Terraform an Autopilot cluster; deploy the *same image* with
      Deployment/Service/Gateway manifests (kustomize), Workload Identity
      for the runtime SA, HPA, liveness/readiness from the existing actuator
      probes.
- [ ] Write the comparison: what GKE bought (control, portability), what it
      cost (manifests, cluster fee, upgrade surface), when it wins for real.
- [ ] **Tear it down** — the cluster is a lab, not the architecture. Keep
      manifests + doc in `deploy/gke/`.

**New surface:** GKE Autopilot, Kubernetes manifests, Workload Identity.

## W5 — Cloud Build mirror

- [ ] Reimplement one deploy (ingestion) as `cloudbuild.yaml` with a trigger,
      alongside the GitHub Actions version.
- [ ] Decision record: Actions+WIF vs Cloud Build — auth model, speed, cost,
      ecosystem. Keep whichever the evidence favors as primary.

## W6 (optional) — Firestore for care-management workflow state

Gap *annotations* (assign to a care manager, snooze until date, notes) are
document-shaped, per-user, join-free — a defensible Firestore fit that
doesn't reopen the operational-store decision (which stands for the gap
records themselves).

- [ ] Firestore in Native mode; API endpoints for annotations; dashboard
      controls.
- [ ] Decision record contrasting this use with the original
      Firestore-vs-Cloud-SQL analysis.

---

## Cost notes

W1 removes cost (no public IP), W2/W3 stage 1 are cents, Vertex custom
training is dollars per run at this data size, W4 GKE Autopilot bills per
pod-hour (this is why it is time-boxed), W5/W6 are free tier. The standing
rule remains: stop Cloud SQL when not demoing; destroy the GKE lab when done.
