#!/usr/bin/env bash
# Feed bundles through the *local* ingestion loop: copies each bundle into the
# service's local read directory and POSTs the synthetic Eventarc CloudEvent.
#
# Prereqs: `docker compose up -d` (HAPI on :8090) and the ingestion service
# running with the local profile on :8080.
#
# Synthea's organization/practitioner directory bundles must be ingested before
# patient bundles because patient transactions contain conditional references
# to those resources. The controller deliberately acknowledges permanent
# failures with HTTP 200, so this script also checks the response body instead
# of treating every 2xx response as a successful ingestion.
#
# Usage (from the repo root):
#   ./tools/local/ingest-local.sh [bundle-dir]
#     bundle-dir  directory of *.json bundles (default tools/synthea/output/fhir)
set -euo pipefail

SRC="${1:-tools/synthea/output/fhir}"
DEST="services/ingestion-service/bundles"
ENDPOINT="${INGESTION_URL:-http://localhost:8080}"

mkdir -p "$DEST"

shopt -s nullglob
infra=("$SRC"/hospitalInformation*.json "$SRC"/practitionerInformation*.json)
patients=()
for f in "$SRC"/*.json; do
  case "$(basename "$f")" in
    hospitalInformation*|practitionerInformation*) ;;
    *) patients+=("$f") ;;
  esac
done
files=("${infra[@]}" "${patients[@]}")

if ((${#files[@]} == 0)); then
  echo "No JSON bundles found in $SRC" >&2
  exit 1
fi

ok=0 skipped=0 fail=0
for f in "${files[@]}"; do
  name="$(basename "$f")"
  cp "$f" "$DEST/$name"

  if ! response_and_status="$(curl -sS -w $'\n%{http_code}' -X POST "$ENDPOINT/" \
      -H 'ce-type: google.cloud.storage.object.v1.finalized' \
      -H 'Content-Type: application/json' \
      -d "{\"bucket\": \"local\", \"name\": \"${name}\"}")"; then
    fail=$((fail + 1))
    echo "FAILED    $name (could not reach ingestion service)" >&2
    continue
  fi

  status="${response_and_status##*$'\n'}"
  body="${response_and_status%$'\n'*}"
  if [[ "$status" =~ ^2[0-9][0-9]$ && "$body" == ingested\ * ]]; then
    ok=$((ok + 1))
    echo "ingested  $name ($body)"
  elif [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
    skipped=$((skipped + 1))
    echo "SKIPPED   $name ($body)" >&2
  else
    fail=$((fail + 1))
    echo "FAILED    $name (HTTP $status: $body)" >&2
  fi
done

echo
echo "Done: ${ok} ingested, ${skipped} skipped, ${fail} failed."
echo "Inspect the store: curl 'http://localhost:8090/fhir/Patient?_summary=count'"

if ((skipped > 0 || fail > 0)); then
  exit 1
fi
