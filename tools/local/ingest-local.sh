#!/usr/bin/env bash
# Feed bundles through the *local* ingestion loop: copies each bundle into the
# service's local read directory and POSTs the synthetic Eventarc CloudEvent.
#
# Prereqs: `docker compose up -d` (HAPI on :8090) and the ingestion service
# running with the local profile on :8080.
#
# Usage (from the repo root):
#   ./tools/local/ingest-local.sh [bundle-dir]
#     bundle-dir  directory of *.json bundles (default tools/synthea/output/fhir)
set -euo pipefail

SRC="${1:-tools/synthea/output/fhir}"
DEST="services/ingestion-service/bundles"
ENDPOINT="${INGESTION_URL:-http://localhost:8080}"

mkdir -p "$DEST"

ok=0 fail=0
for f in "$SRC"/*.json; do
  name="$(basename "$f")"
  cp "$f" "$DEST/$name"
  if curl -sf -X POST "$ENDPOINT/" \
      -H 'ce-type: google.cloud.storage.object.v1.finalized' \
      -H 'Content-Type: application/json' \
      -d "{\"bucket\": \"local\", \"name\": \"${name}\"}" > /dev/null; then
    ok=$((ok + 1))
    echo "ingested  $name"
  else
    fail=$((fail + 1))
    echo "FAILED    $name" >&2
  fi
done

echo
echo "Done: ${ok} ingested, ${fail} failed."
echo "Inspect the store: curl 'http://localhost:8090/fhir/Patient?_summary=count'"
