#!/usr/bin/env bash
# Upload generated Synthea bundles to the ingest bucket, which triggers the
# ingestion service via Eventarc.
#
# Usage: ./upload.sh gs://PROJECT-fhir-ingest
#   (bucket name is in `terraform output ingest_bucket`)
#
# Provider/organization bundles go first as a courtesy to readers of the
# store; correctness does not depend on it because the FHIR store has
# referential integrity disabled (uploads arrive in arbitrary order anyway).
set -euo pipefail

BUCKET="${1:?usage: upload.sh gs://bucket-name}"
cd "$(dirname "$0")/output/fhir"

shopt -s nullglob
infra=(hospitalInformation*.json practitionerInformation*.json)
if ((${#infra[@]})); then
  gcloud storage cp "${infra[@]}" "${BUCKET}/"
fi

patients=()
for f in *.json; do
  [[ "$f" == hospitalInformation* || "$f" == practitionerInformation* ]] || patients+=("$f")
done
if ((${#patients[@]})); then
  gcloud storage cp "${patients[@]}" "${BUCKET}/"
fi

echo "Uploaded $((${#infra[@]} + ${#patients[@]})) bundles to ${BUCKET}"
