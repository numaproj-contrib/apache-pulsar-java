#!/usr/bin/env bash
# Publish messages to a Pulsar topic by invoking an encoder script per message and POSTing the output.
# All connection config from .env: PULSAR_ADMIN_URL, PULSAR_TOPIC, PULSAR_AUTH_TOKEN.
#
# Usage: publish-messages.sh <schema-file> [topic] [num-messages] [data-file]
#
# First argument: path to schema file (Avro JSON or Pulsar format). Used by the generic encoder.
# topic         Optional. Full topic (persistent://tenant/namespace/name). Default: PULSAR_TOPIC from .env.
# num-messages  Optional. How many messages to send. Default: 15 or NUM_MESSAGES from .env.
# data-file     Optional. JSON file with one record matching the schema. If omitted, encoder uses defaults.
#
# Examples:
#   publish-messages.sh schema-test-topic-avro.json
#   publish-messages.sh schema-test-topic-avro-name-only.json persistent://tenant/ns/my-topic 10
#   publish-messages.sh schema-test-topic-avro.json persistent://tenant/ns/my-topic 5 record.json

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -f "${SCRIPT_DIR}/.env" ]]; then
  echo "Error: .env file not found. Copy .env.example to .env and set PULSAR_ADMIN_URL, PULSAR_TOPIC, PULSAR_AUTH_TOKEN." >&2
  exit 1
fi
source "${SCRIPT_DIR}/.env"

if [[ -z "${PULSAR_ADMIN_URL:-}" ]]; then
  echo "Error: PULSAR_ADMIN_URL is not set in .env." >&2
  exit 1
fi
if [[ -z "${PULSAR_AUTH_TOKEN:-}" ]]; then
  echo "Error: PULSAR_AUTH_TOKEN is not set in .env." >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") <schema-file> [topic] [num-messages] [data-file]" >&2
  echo "  schema-file   Path to Avro schema (JSON or Pulsar format)." >&2
  echo "  topic         Optional. Default: PULSAR_TOPIC from .env." >&2
  echo "  num-messages  Optional. Default: 15 or NUM_MESSAGES from .env." >&2
  echo "  data-file     Optional. JSON record matching the schema. If omitted, encoder uses defaults." >&2
  exit 1
fi

SCHEMA_FILE="$1"
if [[ "$SCHEMA_FILE" != /* ]]; then
  SCHEMA_FILE="${SCRIPT_DIR}/${SCHEMA_FILE}"
fi
if [[ ! -f "$SCHEMA_FILE" ]]; then
  echo "Error: Schema file not found: $SCHEMA_FILE" >&2
  exit 1
fi

ENCODER_SCRIPT="${SCRIPT_DIR}/encode_avro_generic.py"
if [[ ! -f "$ENCODER_SCRIPT" ]]; then
  echo "Error: Encoder script not found: $ENCODER_SCRIPT" >&2
  exit 1
fi

if [[ -n "${2:-}" ]]; then
  TOPIC="$2"
else
  TOPIC="${PULSAR_TOPIC:-}"
  if [[ -z "$TOPIC" ]]; then
    echo "Error: PULSAR_TOPIC is not set in .env and no topic argument was provided." >&2
    exit 1
  fi
fi

NUM_MESSAGES="${3:-${NUM_MESSAGES:-15}}"
DATA_FILE="${4:-}"

# Parse persistent://tenant/namespace/topic-name
if [[ ! "$TOPIC" =~ ^persistent://([^/]+)/([^/]+)/([^/]+)$ ]]; then
  echo "Error: Topic must be persistent://tenant/namespace/topic-name (got: $TOPIC)" >&2
  exit 1
fi
TENANT="${BASH_REMATCH[1]}"
NAMESPACE="${BASH_REMATCH[2]}"
TOPIC_NAME="${BASH_REMATCH[3]}"

BASE_URL="${PULSAR_ADMIN_URL%/}"
URL="${BASE_URL}/admin/rest/topics/v1/persistent/${TENANT}/${NAMESPACE}/${TOPIC_NAME}/message"

echo "Publishing $NUM_MESSAGES messages to $TOPIC (schema: $SCHEMA_FILE) ..."
echo ""

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

success=0
rejected=0
for i in $(seq 1 "$NUM_MESSAGES"); do
  if [[ -n "$DATA_FILE" ]]; then
    python3 "$ENCODER_SCRIPT" "$SCHEMA_FILE" "$DATA_FILE" > "$TMP" || { echo "Error: Failed to encode message." >&2; exit 1; }
  else
    python3 "$ENCODER_SCRIPT" "$SCHEMA_FILE" > "$TMP" || { echo "Error: Failed to encode message." >&2; exit 1; }
  fi
  code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 --max-time 30 -X POST "$URL" \
    -H "Authorization: Bearer ${PULSAR_AUTH_TOKEN}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$TMP")
  if [[ "$code" -ge 200 && "$code" -lt 300 ]]; then
    (( success++ )) || true
    echo "  message $i -> HTTP $code"
  else
    (( rejected++ )) || true
    echo "  message $i -> HTTP $code (rejected)"
  fi
done

echo ""
echo "Done: $success published, $rejected rejected."
