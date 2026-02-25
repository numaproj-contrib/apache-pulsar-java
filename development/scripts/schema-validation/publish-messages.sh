#!/usr/bin/env bash
# Publish messages to a Pulsar topic by invoking an encoder script per message and POSTing the output.
# All connection config from .env: PULSAR_ADMIN_URL, PULSAR_TOPIC, PULSAR_AUTH_TOKEN.
#
# Usage: publish-messages.sh <encoder-script> [topic] [num-messages]
#   encoder-script   Path to script that writes one message (binary) to stdout. Called once per message.
#   topic            Full topic (persistent://tenant/namespace/name). Optional; uses PULSAR_TOPIC from .env.
#   num-messages     How many messages to send. Optional; default 15 (or NUM_MESSAGES from .env).
#
# Encoder is invoked with no arguments; it writes one message (binary) to stdout.
# Examples:
#   publish-messages.sh avro_encode_full.py
#   publish-messages.sh avro_encode_name_only.py persistent://tenant/ns/my-topic 10

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
  echo "Usage: $(basename "$0") <encoder-script> [topic] [num-messages]" >&2
  echo "  encoder-script   Script that writes one message to stdout (e.g. avro_encode_full.py)." >&2
  echo "  topic            Optional. Default: PULSAR_TOPIC from .env." >&2
  echo "  num-messages     Optional. Default: 15 or NUM_MESSAGES from .env." >&2
  exit 1
fi

ENCODER_SCRIPT="$1"
if [[ "$ENCODER_SCRIPT" != /* ]]; then
  ENCODER_SCRIPT="${SCRIPT_DIR}/${ENCODER_SCRIPT}"
fi
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

echo "Publishing $NUM_MESSAGES messages to $TOPIC via $ENCODER_SCRIPT ..."
echo ""

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

success=0
rejected=0
for i in $(seq 1 "$NUM_MESSAGES"); do
  python3 "$ENCODER_SCRIPT" > "$TMP" || { echo "Error: Failed to encode message." >&2; exit 1; }
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
