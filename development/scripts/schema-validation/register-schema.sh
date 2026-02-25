#!/usr/bin/env bash
# Upload a schema to a Pulsar topic using pulsarctl.
# All connection config (PULSAR_ADMIN_URL, PULSAR_TOPIC, PULSAR_AUTH_TOKEN) must be in .env.
# Usage: register-schema.sh <schema-file> [topic]
#   schema-file  Path to the schema JSON file (required).
#   topic        Full topic name (e.g. persistent://tenant/namespace/topic-name). Optional; uses PULSAR_TOPIC from .env if omitted.
# Prerequisites: pulsarctl installed (e.g. brew install pulsarctl).

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
  echo "Usage: $(basename "$0") <schema-file> [topic]" >&2
  echo "  schema-file  Path to the schema JSON file." >&2
  echo "  topic        Optional. Full topic (e.g. persistent://tenant/namespace/topic-name). Default: PULSAR_TOPIC from .env." >&2
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

if [[ -n "${2:-}" ]]; then
  TOPIC="$2"
else
  TOPIC="${PULSAR_TOPIC:-}"
  if [[ -z "$TOPIC" ]]; then
    echo "Error: PULSAR_TOPIC is not set in .env and no topic argument was provided." >&2
    exit 1
  fi
fi

echo "Uploading schema from $SCHEMA_FILE to $TOPIC ..."
pulsarctl schemas upload "$TOPIC" \
  --filename "$SCHEMA_FILE" \
  --admin-service-url "$PULSAR_ADMIN_URL" \
  --auth-plugin org.apache.pulsar.client.impl.auth.AuthenticationToken \
  --auth-params "token:$PULSAR_AUTH_TOKEN" \
  --tls-allow-insecure

# Enable schema validation enforcement on the topic's namespace.
NAMESPACE=$(echo "$TOPIC" | sed -n 's|persistent://\([^/]*/[^/]*\)/.*|\1|p')
if [[ -z "$NAMESPACE" ]]; then
  echo "Warning: Could not parse tenant/namespace from TOPIC ($TOPIC); skipping set-schema-validation-enforced." >&2
else
  echo "Enabling schema validation enforcement for namespace $NAMESPACE ..."
  pulsarctl namespaces set-schema-validation-enforced "$NAMESPACE" \
    --admin-service-url "$PULSAR_ADMIN_URL" \
    --auth-plugin org.apache.pulsar.client.impl.auth.AuthenticationToken \
    --auth-params "token:$PULSAR_AUTH_TOKEN" \
    --tls-allow-insecure
fi

echo "Done."
