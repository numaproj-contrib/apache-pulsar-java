#!/usr/bin/env bash
set -euo pipefail

# Captures throughput, latency, and resource utilization from a Numaflow MonoVertex.
# Runs 3 measurement rounds and reports the median to reduce variance from noisy CI runners.
# Outputs a JSON file compatible with github-action-benchmark.
#
# Usage: ./capture-metrics.sh [measurement_seconds] [num_rounds]
#   measurement_seconds: how long each measurement round lasts (default 90)
#   num_rounds: number of rounds to run (default 3)

DURATION=${1:-90}
NUM_ROUNDS=${2:-3}
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

# --- latencies (microseconds → milliseconds per batch) ---
# Average = (sum_B - sum_A) / (count_B - count_A) / 1000
calc_avg_latency_ms() {
  local snap_a=$1 snap_b=$2 metric=$3
  local sum_a count_a sum_b count_b
  sum_a=$(extract_metric "${snap_a}" "${metric}_sum")
  sum_b=$(extract_metric "${snap_b}" "${metric}_sum")
  count_a=$(extract_metric "${snap_a}" "${metric}_count")
  count_b=$(extract_metric "${snap_b}" "${metric}_count")
  awk "BEGIN {
    d = ${count_b} - ${count_a};
    if (d > 0) printf \"%.2f\", (${sum_b} - ${sum_a}) / d / 1000;
    else printf \"0\";
  }"
}

median_of_three() {
  echo -e "$1\n$2\n$3" | sort -g | sed -n '2p'
}

stats() {
  local a=$1 b=$2 c=$3
  awk "BEGIN {
    min = ($a < $b) ? $a : $b; if ($c < min) min = $c;
    max = ($a > $b) ? $a : $b; if ($c > max) max = $c;
    mean = ($a + $b + $c) / 3;
    var = (($a-mean)^2 + ($b-mean)^2 + ($c-mean)^2) / 3;
    printf \"min=%.2f max=%.2f mean=%.2f stddev=%.2f\", min, max, mean, sqrt(var);
  }"
}

# ── Measurement rounds ────────────────────────────────────────────
echo "Warming up (60s)..."
sleep 60

sample_resources &
SAMPLE_PID=$!
trap "kill ${PF_PID} 2>/dev/null || true; kill ${SAMPLE_PID} 2>/dev/null || true; rm -f ${RESOURCE_LOG} ${SNAP_A} ${SNAP_B}" EXIT

declare -a R_THROUGHPUT R_PROC_LAT R_READ_LAT R_ACK_LAT

for round in $(seq 1 "${NUM_ROUNDS}"); do
  echo ""
  echo "=== Round ${round}/${NUM_ROUNDS} (${DURATION}s) ==="

  scrape_metrics "${SNAP_A}"
  START_TS=$(date +%s)
  sleep "${DURATION}"
  scrape_metrics "${SNAP_B}"
  END_TS=$(date +%s)
  ELAPSED=$((END_TS - START_TS))

  if [ "${ELAPSED}" -le 0 ]; then
    echo "ERROR: elapsed time is zero in round ${round}"
    exit 1
  fi

  READ_A=$(extract_metric "${SNAP_A}" "monovtx_read_total")
  READ_B=$(extract_metric "${SNAP_B}" "monovtx_read_total")
  MESSAGES=$(awk "BEGIN {printf \"%.0f\", ${READ_B} - ${READ_A}}")

  tp=$(awk "BEGIN {printf \"%.2f\", ${MESSAGES} / ${ELAPSED}}")
  pl=$(calc_avg_latency_ms "${SNAP_A}" "${SNAP_B}" "monovtx_processing_time")
  rl=$(calc_avg_latency_ms "${SNAP_A}" "${SNAP_B}" "monovtx_read_time")
  al=$(calc_avg_latency_ms "${SNAP_A}" "${SNAP_B}" "monovtx_ack_time")

  R_THROUGHPUT+=("$tp")
  R_PROC_LAT+=("$pl")
  R_READ_LAT+=("$rl")
  R_ACK_LAT+=("$al")

  echo "  Throughput         : ${tp} msgs/sec"
  echo "  Processing Latency : ${pl} ms"
  echo "  Read Latency       : ${rl} ms"
  echo "  Ack Latency        : ${al} ms"
done

kill ${SAMPLE_PID} 2>/dev/null || true

# ── Select medians ────────────────────────────────────────────────
THROUGHPUT=$(median_of_three "${R_THROUGHPUT[0]}" "${R_THROUGHPUT[1]}" "${R_THROUGHPUT[2]}")
PROCESSING_LATENCY=$(median_of_three "${R_PROC_LAT[0]}" "${R_PROC_LAT[1]}" "${R_PROC_LAT[2]}")
READ_LATENCY=$(median_of_three "${R_READ_LAT[0]}" "${R_READ_LAT[1]}" "${R_READ_LAT[2]}")
ACK_LATENCY=$(median_of_three "${R_ACK_LAT[0]}" "${R_ACK_LAT[1]}" "${R_ACK_LAT[2]}")

# ── Resource utilization (averaged across all rounds) ─────────────
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

# ── Report ────────────────────────────────────────────────────────
echo ""
echo "=== Per-round values ==="
echo "  Throughput         : ${R_THROUGHPUT[*]}"
echo "  Processing Latency : ${R_PROC_LAT[*]}"
echo "  Read Latency       : ${R_READ_LAT[*]}"
echo "  Ack Latency        : ${R_ACK_LAT[*]}"
echo ""
echo "=== Variance ==="
echo "  Throughput         : $(stats "${R_THROUGHPUT[0]}" "${R_THROUGHPUT[1]}" "${R_THROUGHPUT[2]}")"
echo "  Processing Latency : $(stats "${R_PROC_LAT[0]}" "${R_PROC_LAT[1]}" "${R_PROC_LAT[2]}")"
echo "  Read Latency       : $(stats "${R_READ_LAT[0]}" "${R_READ_LAT[1]}" "${R_READ_LAT[2]}")"
echo "  Ack Latency        : $(stats "${R_ACK_LAT[0]}" "${R_ACK_LAT[1]}" "${R_ACK_LAT[2]}")"
echo ""
echo "=== Median results (reported) ==="
echo "Throughput            : ${THROUGHPUT} msgs/sec"
echo "Processing Latency    : ${PROCESSING_LATENCY} ms/batch"
echo "Read Latency          : ${READ_LATENCY} ms/batch"
echo "Ack Latency           : ${ACK_LATENCY} ms/batch"
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
