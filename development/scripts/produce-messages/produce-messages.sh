#!/usr/bin/env bash
#
# Produce distinguishable messages to Pulsar topics on StreamNative.
# Uses StreamNative Message REST API (curl). Safe to run locally or over SSH.
#
# Loads from ./.env (if present in this directory).
# Required env: PULSAR_REST_URL, PULSAR_AUTH_TOKEN (or use .env)
# Optional: PULSAR_TENANT (default demo), PULSAR_NAMESPACE (default dev),
#            COUNT (default 5), TOPICS (comma-separated, default test-topic,test-topic-2)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Load .env from this script's directory if present
if [[ -f "${SCRIPT_DIR}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${SCRIPT_DIR}/.env"
  set +a
fi

BASE_URL="${PULSAR_REST_URL:?Set PULSAR_REST_URL or add to .env in this directory (see README)}"
# Strip trailing slash
BASE_URL="${BASE_URL%/}"
TOKEN="${PULSAR_AUTH_TOKEN:?Set PULSAR_AUTH_TOKEN or add to .env in this directory (see README)}"
TENANT="${PULSAR_TENANT:-demo}"
NAMESPACE="${PULSAR_NAMESPACE:-dev}"
COUNT="${COUNT:-5}"
# Parse TOPICS into array (comma-separated, default: test-topic,test-topic-2)
IFS=',' read -ra TOPIC_ARR <<< "${TOPICS:-test-topic,test-topic-2}"
for i in "${!TOPIC_ARR[@]}"; do
  TOPIC_ARR[$i]=$(echo "${TOPIC_ARR[$i]}" | xargs)
done

# REST path: /admin/rest/topics/v1/persistent/<tenant>/<namespace>/<topic>/message
publish() {
  local topic="$1"
  local body="$2"
  local url="${BASE_URL}/admin/rest/topics/v1/persistent/${TENANT}/${NAMESPACE}/${topic}/message"
  local resp
  resp=$(curl -s -w "\n%{http_code}" --connect-timeout 10 --max-time 30 -X POST "$url" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "$body")
  local code
  code=$(echo "$resp" | tail -n1)
  local body_resp
  body_resp=$(echo "$resp" | sed '$d')
  if [[ "$code" -ge 200 && "$code" -lt 300 ]]; then
    echo "  OK $topic (HTTP $code) -> $body_resp"
  else
    echo "  FAILED $topic (HTTP $code) $body_resp" >&2
    return 1
  fi
}

echo "Publishing to tenant=$TENANT namespace=$NAMESPACE topics=(${TOPIC_ARR[*]}) (${COUNT} messages per topic)..."
echo ""

for i in $(seq 1 "$COUNT"); do
  ts=$(date -u '+%Y-%m-%d %H:%M:%S UTC')
  for topic in "${TOPIC_ARR[@]}"; do
    echo "[$topic] Message $i at $ts"
    publish "$topic" "[$topic] Message $i at $ts"
  done
  echo ""
done

echo "Done. Messages are distinguishable by the [topic-name] prefix."
