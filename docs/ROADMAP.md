# Roadmap — Growth Workstreams

The v1 pipeline is complete (see [ARCHITECTURE.md](./ARCHITECTURE.md)). These
workstreams extend it deliberately across more of the GCP platform. Each is
scoped to be independently completable and ends with a short decision-record
note in `docs/` — additions that contradict a v1 decision get an explicit
amendment, the same way the dashboard-hosting and schema-ownership changes
were recorded.

Suggested order: W0 → W1 → W2. W6 is optional.

> **Scope note:** the ML track, GKE comparison, and Cloud Build workstreams
> that originally lived here (W3–W5) moved to a companion project — a small,
> hands-built ML system where those tools are the primary runtime rather than
> a retrofit. This repo stays the architecture/platform showcase; the
> companion is the ML depth piece.

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

W1 removes cost (no public IP); W2 and W6 sit in free tier. The standing
rule remains: stop Cloud SQL when not demoing.
