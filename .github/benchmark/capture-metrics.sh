#!/usr/bin/env bash
set -euo pipefail

# Captures throughput from a Numaflow MonoVertex by scraping its Prometheus
# metrics endpoint. Outputs a JSON file compatible with github-action-benchmark.
#
# Usage: ./capture-metrics.sh [measurement_seconds]
#   measurement_seconds: how long to measure steady-state throughput (default 120)

DURATION=${1:-120}
MVTX_NAME="consumer-mvtx"
METRICS_PORT=2469
OUTPUT_FILE="benchmark-results.json"

echo "=== Benchmark metrics capture ==="
echo "Measurement duration: ${DURATION}s"

# Wait for the MonoVertex pod to be running
echo "Waiting for consumer MonoVertex pod to be ready..."
kubectl wait --for=condition=Ready pod \
  -l "numaflow.numaproj.io/mono-vertex-name=${MVTX_NAME}" \
  --timeout=180s

POD=$(kubectl get pod \
  -l "numaflow.numaproj.io/mono-vertex-name=${MVTX_NAME}" \
  -o jsonpath='{.items[0].metadata.name}')
echo "Found pod: ${POD}"

# Port-forward to the metrics endpoint (MonoVertex uses HTTPS internally)
kubectl port-forward "${POD}" "${METRICS_PORT}:${METRICS_PORT}" &
PF_PID=$!
trap "kill ${PF_PID} 2>/dev/null || true" EXIT
sleep 5

# Helper: scrape monovtx_read_total and sum across all partitions/replicas
get_read_total() {
  local raw
  raw=$(curl -sk "https://localhost:${METRICS_PORT}/metrics" 2>/dev/null || \
        curl -s  "http://localhost:${METRICS_PORT}/metrics"  2>/dev/null || echo "")

  if [ -z "$raw" ]; then
    echo "0"
    return
  fi

  # Dump all available metrics on first call for debugging
  if [ "${DUMP_METRICS:-0}" = "1" ]; then
    echo "$raw" | grep -E '^[a-z]' | head -60 >&2
    DUMP_METRICS=0
  fi

  local total
  total=$(echo "$raw" \
    | grep '^monovtx_read_total' \
    | awk '{s+=$2} END {printf "%.0f", s}')
  echo "${total:-0}"
}

echo "Warming up (30s)..."
sleep 30

# --- initial snapshot ---
export DUMP_METRICS=1
INITIAL=$(get_read_total)
echo "Initial read total: ${INITIAL}"
START_TS=$(date +%s)

echo "Measuring for ${DURATION}s..."
sleep "${DURATION}"

# --- final snapshot ---
FINAL=$(get_read_total)
END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))

echo "Final read total: ${FINAL}"
MESSAGES=$((FINAL - INITIAL))

if [ "${ELAPSED}" -le 0 ]; then
  echo "ERROR: elapsed time is zero"
  exit 1
fi

THROUGHPUT=$(awk "BEGIN {printf \"%.2f\", ${MESSAGES} / ${ELAPSED}}")

echo ""
echo "=== Results ==="
echo "Messages processed : ${MESSAGES}"
echo "Elapsed            : ${ELAPSED}s"
echo "Throughput         : ${THROUGHPUT} msgs/sec"

cat > "${OUTPUT_FILE}" <<EOF
[
  {
    "name": "Consumer Throughput",
    "unit": "msgs/sec",
    "value": ${THROUGHPUT}
  }
]
EOF

echo "Wrote ${OUTPUT_FILE}"
