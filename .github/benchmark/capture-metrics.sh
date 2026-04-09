#!/usr/bin/env bash
set -euo pipefail

# Captures throughput, latency, and resource utilization from a Numaflow MonoVertex.
# Outputs a JSON file compatible with github-action-benchmark.
#
# Usage: ./capture-metrics.sh [measurement_seconds]
#   measurement_seconds: how long to measure steady-state throughput (default 120)

DURATION=${1:-120}
MVTX_NAME="consumer-mvtx"
METRICS_PORT=2469
OUTPUT_FILE="benchmark-results.json"
RESOURCE_LOG=$(mktemp)
SNAP_A=$(mktemp)
SNAP_B=$(mktemp)

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
trap "kill ${PF_PID} 2>/dev/null || true; rm -f ${RESOURCE_LOG} ${SNAP_A} ${SNAP_B}" EXIT
sleep 5

# Scrape all metrics into a file
scrape_metrics() {
  local dest=$1
  curl -sk "https://localhost:${METRICS_PORT}/metrics" 2>/dev/null > "${dest}" || \
    curl -s  "http://localhost:${METRICS_PORT}/metrics"  2>/dev/null > "${dest}" || \
    echo "" > "${dest}"
}

# Extract a single metric value (sum across replicas)
extract_metric() {
  local file=$1
  local name=$2
  grep "^${name}" "${file}" 2>/dev/null \
    | awk '{s+=$2} END {printf "%.2f", s}' || echo "0"
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

# --- snapshot A ---
scrape_metrics "${SNAP_A}"
echo "=== Snapshot A metrics ==="
grep -E '^monovtx_(read_total|ack_total|processing_time_sum|processing_time_count|read_time_sum|read_time_count|ack_time_sum|ack_time_count)' "${SNAP_A}" || true

START_TS=$(date +%s)

# Start resource sampling in background
sample_resources &
SAMPLE_PID=$!
trap "kill ${PF_PID} 2>/dev/null || true; kill ${SAMPLE_PID} 2>/dev/null || true; rm -f ${RESOURCE_LOG} ${SNAP_A} ${SNAP_B}" EXIT

echo "Measuring for ${DURATION}s..."
sleep "${DURATION}"

# Stop resource sampling
kill ${SAMPLE_PID} 2>/dev/null || true

# --- snapshot B ---
scrape_metrics "${SNAP_B}"
END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))

echo "=== Snapshot B metrics ==="
grep -E '^monovtx_(read_total|ack_total|processing_time_sum|processing_time_count|read_time_sum|read_time_count|ack_time_sum|ack_time_count)' "${SNAP_B}" || true

# --- throughput ---
READ_A=$(extract_metric "${SNAP_A}" "monovtx_read_total")
READ_B=$(extract_metric "${SNAP_B}" "monovtx_read_total")
MESSAGES=$(awk "BEGIN {printf \"%.0f\", ${READ_B} - ${READ_A}}")

if [ "${ELAPSED}" -le 0 ]; then
  echo "ERROR: elapsed time is zero"
  exit 1
fi

THROUGHPUT=$(awk "BEGIN {printf \"%.2f\", ${MESSAGES} / ${ELAPSED}}")

# --- latencies (microseconds → milliseconds per batch) ---
# Average = (sum_B - sum_A) / (count_B - count_A) / 1000
calc_avg_latency_ms() {
  local sum_a count_a sum_b count_b
  sum_a=$(extract_metric "${SNAP_A}" "$1_sum")
  sum_b=$(extract_metric "${SNAP_B}" "$1_sum")
  count_a=$(extract_metric "${SNAP_A}" "$1_count")
  count_b=$(extract_metric "${SNAP_B}" "$1_count")
  awk "BEGIN {
    d = ${count_b} - ${count_a};
    if (d > 0) printf \"%.2f\", (${sum_b} - ${sum_a}) / d / 1000;
    else printf \"0\";
  }"
}

PROCESSING_LATENCY=$(calc_avg_latency_ms "monovtx_processing_time")
READ_LATENCY=$(calc_avg_latency_ms "monovtx_read_time")
ACK_LATENCY=$(calc_avg_latency_ms "monovtx_ack_time")

# --- resource utilization ---
AVG_CPU="0"
AVG_MEM="0"
SAMPLE_COUNT=0

if [ -s "${RESOURCE_LOG}" ]; then
  echo ""
  echo "=== Resource samples ==="
  cat "${RESOURCE_LOG}"

  SAMPLE_COUNT=$(wc -l < "${RESOURCE_LOG}" | tr -d ' ')
  CPU_SUM=$(awk '{gsub(/m$/,"",$2); s+=$2} END {printf "%.0f", s}' "${RESOURCE_LOG}")
  MEM_SUM=$(awk '{gsub(/Mi$/,"",$3); s+=$3} END {printf "%.0f", s}' "${RESOURCE_LOG}")

  if [ "${SAMPLE_COUNT}" -gt 0 ]; then
    AVG_CPU=$(awk "BEGIN {printf \"%.0f\", ${CPU_SUM} / ${SAMPLE_COUNT}}")
    AVG_MEM=$(awk "BEGIN {printf \"%.0f\", ${MEM_SUM} / ${SAMPLE_COUNT}}")
  fi
fi

echo ""
echo "=== Results ==="
echo "Messages processed    : ${MESSAGES}"
echo "Elapsed               : ${ELAPSED}s"
echo "Throughput            : ${THROUGHPUT} msgs/sec"
echo "Avg Processing Latency: ${PROCESSING_LATENCY} ms/batch"
echo "Avg Read Latency      : ${READ_LATENCY} ms/batch"
echo "Avg Ack Latency       : ${ACK_LATENCY} ms/batch"
echo "Avg CPU               : ${AVG_CPU}m (${SAMPLE_COUNT} samples)"
echo "Avg Memory            : ${AVG_MEM}Mi (${SAMPLE_COUNT} samples)"

cat > "${OUTPUT_FILE}" <<EOF
[
  {
    "name": "Consumer Throughput",
    "unit": "msgs/sec",
    "value": ${THROUGHPUT}
  },
  {
    "name": "Processing Latency (per batch)",
    "unit": "ms",
    "value": ${PROCESSING_LATENCY}
  },
  {
    "name": "Read Latency (per batch)",
    "unit": "ms",
    "value": ${READ_LATENCY}
  },
  {
    "name": "Ack Latency (per batch)",
    "unit": "ms",
    "value": ${ACK_LATENCY}
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
