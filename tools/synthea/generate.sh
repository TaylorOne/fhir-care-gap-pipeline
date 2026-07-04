#!/usr/bin/env bash
# Generate synthetic FHIR R4 transaction bundles with Synthea.
#
# Usage: ./generate.sh [population] [state]
#   population  number of living patients to generate (default 25)
#   state       US state for demographics (default Massachusetts)
#
# Output lands in tools/synthea/output/fhir/ as one JSON bundle per patient,
# plus hospitalInformation*.json and practitionerInformation*.json.
set -euo pipefail

POPULATION="${1:-25}"
STATE="${2:-Massachusetts}"
SYNTHEA_VERSION="${SYNTHEA_VERSION:-v3.3.0}"

cd "$(dirname "$0")"

JAR="synthea-with-dependencies-${SYNTHEA_VERSION}.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Downloading Synthea ${SYNTHEA_VERSION}..."
  curl -fL -o "$JAR" \
    "https://github.com/synthetichealth/synthea/releases/download/${SYNTHEA_VERSION}/synthea-with-dependencies.jar"
fi

java -jar "$JAR" -c synthea.properties -p "$POPULATION" "$STATE"

COUNT=$(find output/fhir -name '*.json' | wc -l | tr -d ' ')
echo
echo "Generated ${COUNT} bundles in $(pwd)/output/fhir"
