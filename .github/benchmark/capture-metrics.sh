#!/usr/bin/env bash
set -euo pipefail

# Captures throughput + resource utilization from a Numaflow MonoVertex.
# Outputs a JSON file compatible with github-action-benchmark.
#
# Usage: ./capture-metrics.sh [measurement_seconds]
#   measurement_seconds: how long to measure steady-state throughput (default 120)

DURATION=${1:-120}
MVTX_NAME="consumer-mvtx"
METRICS_PORT=2469
OUTPUT_FILE="benchmark-results.json"
RESOURCE_LOG=$(mktemp)

echo "=== Benchmark metrics capture ==="
echo "Measurement duration: ${DURATION}s"

# Wait for pod to exist, then wait for it to be ready
echo "Waiting for consumer MonoVertex pod to exist..."
for i in $(seq 1 60); do
  if kubectl get pod -l "numaflow.numaproj.io/mono-vertex-name=${MVTX_NAME}" 2>/dev/null | grep -q "${MVTX_NAME}"; then
    echo "Pod found after ${i}s"
    break
  fi
  sleep 1
done

echo "Waiting for pod to be ready..."
kubectl wait --for=condition=Ready pod \
  -l "numaflow.numaproj.io/mono-vertex-name=${MVTX_NAME}" \
  --timeout=180s

POD=$(kubectl get pod \
  -l "numaflow.numaproj.io/mono-vertex-name=${MVTX_NAME}" \
  -o jsonpath='{.items[0].metadata.name}')
echo "Found pod: ${POD}"

# Port-forward to the metrics endpoint
kubectl port-forward "${POD}" "${METRICS_PORT}:${METRICS_PORT}" &
PF_PID=$!
trap "kill ${PF_PID} 2>/dev/null || true; rm -f ${RESOURCE_LOG}" EXIT
sleep 5

# Helper: scrape monovtx_read_total and sum across all replicas
get_read_total() {
  local raw
  raw=$(curl -sk "https://localhost:${METRICS_PORT}/metrics" 2>/dev/null || \
        curl -s  "http://localhost:${METRICS_PORT}/metrics"  2>/dev/null || echo "")

  if [ -z "$raw" ]; then
    echo "0"
    return
  fi

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

# Background loop: sample kubectl top every 10s and log to file
sample_resources() {
  while true; do
    local line
    line=$(kubectl top pod "${POD}" --no-headers 2>/dev/null || echo "")
    if [ -n "$line" ]; then
      echo "$line" >> "${RESOURCE_LOG}"
    fi
    sleep 10
  done
}

echo "Warming up (30s)..."
sleep 30

# --- initial snapshot ---
export DUMP_METRICS=1
INITIAL=$(get_read_total)
echo "Initial read total: ${INITIAL}"
START_TS=$(date +%s)

# Start resource sampling in background
sample_resources &
SAMPLE_PID=$!
trap "kill ${PF_PID} 2>/dev/null || true; kill ${SAMPLE_PID} 2>/dev/null || true; rm -f ${RESOURCE_LOG}" EXIT

echo "Measuring for ${DURATION}s..."
sleep "${DURATION}"

# Stop resource sampling
kill ${SAMPLE_PID} 2>/dev/null || true

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

# --- parse resource samples ---
# kubectl top output: "pod-name   250m   180Mi"
# Extract CPU (millicores) and memory (MiB), compute averages
AVG_CPU="0"
AVG_MEM="0"
SAMPLE_COUNT=0

if [ -s "${RESOURCE_LOG}" ]; then
  echo ""
  echo "=== Resource samples ==="
  cat "${RESOURCE_LOG}"

  SAMPLE_COUNT=$(wc -l < "${RESOURCE_LOG}" | tr -d ' ')
  # CPU: strip trailing 'm', sum, average
  CPU_SUM=$(awk '{gsub(/m$/,"",$2); s+=$2} END {printf "%.0f", s}' "${RESOURCE_LOG}")
  # Memory: strip trailing 'Mi', sum, average
  MEM_SUM=$(awk '{gsub(/Mi$/,"",$3); s+=$3} END {printf "%.0f", s}' "${RESOURCE_LOG}")

  if [ "${SAMPLE_COUNT}" -gt 0 ]; then
    AVG_CPU=$(awk "BEGIN {printf \"%.0f\", ${CPU_SUM} / ${SAMPLE_COUNT}}")
    AVG_MEM=$(awk "BEGIN {printf \"%.0f\", ${MEM_SUM} / ${SAMPLE_COUNT}}")
  fi
fi

echo ""
echo "=== Results ==="
echo "Messages processed : ${MESSAGES}"
echo "Elapsed            : ${ELAPSED}s"
echo "Throughput         : ${THROUGHPUT} msgs/sec"
echo "Avg CPU            : ${AVG_CPU}m (${SAMPLE_COUNT} samples)"
echo "Avg Memory         : ${AVG_MEM}Mi (${SAMPLE_COUNT} samples)"

cat > "${OUTPUT_FILE}" <<EOF
[
  {
    "name": "Consumer Throughput",
    "unit": "msgs/sec",
    "value": ${THROUGHPUT}
  },
  {
    "name": "Consumer CPU",
    "unit": "millicores",
    "value": ${AVG_CPU}
  },
  {
    "name": "Consumer Memory",
    "unit": "MiB",
    "value": ${AVG_MEM}
  }
]
EOF

echo "Wrote ${OUTPUT_FILE}"
