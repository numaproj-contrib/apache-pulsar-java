# Testing

This page covers both the automated performance benchmarking that runs in CI and the helper scripts for manual testing.

---

## Performance Testing

### Automated CI Benchmarks

Every push to `main` and every pull request triggers the **Benchmark** workflow (`.github/workflows/benchmark.yml`).

!!! info "What the workflow does"
    1. **Builds** the application image from the current commit using Jib
    2. **Creates a k3d cluster** with Numaflow, JetStream ISB, and a standalone Pulsar instance
    3. **Pre-fills** a Pulsar topic with ~4,000,000+ messages via a generator-based producer pipeline
    4. **Deploys the consumer MonoVertex** and captures metrics over a 90-second window
    5. **Reports results:**
        - **Push to `main`** → stores on the [`gh-pages`](https://github.com/numaproj-contrib/apache-pulsar-java/tree/gh-pages) branch
        - **Pull request** → posts a sticky comment comparing against `main` baseline

### Performance Charts

Benchmark results from every push to `main` are published using [github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark), producing an interactive chart that tracks performance over time.

**[View the charts →](https://numaproj-contrib.github.io/apache-pulsar-java/dev/bench/)**

The chart tracks:

| Metric | Description |
|---|---|
| **Throughput** | Messages per second |
| **Latency** | End-to-end processing time |
| **Resource utilization** | CPU and memory |

An alert threshold of **200%** is configured — if a metric degrades by more than 2x, it will be flagged.

### PR Benchmark Comments

On pull requests, the workflow posts a sticky comment comparing the PR's metrics against the latest `main` baseline. Reviewers can see the performance impact before merging.

### Manual Trigger

The benchmark workflow supports `workflow_dispatch` with configurable parameters:

| Parameter | Default | Description |
|---|---|---|
| `measurement_duration` | `90` | Duration of each measurement round in seconds |
| `prefill_duration` | `500` | How long to run the producer before starting the consumer |

To trigger manually, go to the [Actions tab](https://github.com/numaproj-contrib/apache-pulsar-java/actions/workflows/benchmark.yml) and click "Run workflow".

### Local Performance Testing

For more in-depth testing on your own cluster, see the [performance testing runbook](https://github.com/numaproj-contrib/apache-pulsar-java/tree/main/development/performance-testing). The runbook is designed so that two builds of the consumer can be compared apples-to-apples on the same cluster against the same Pulsar backlog.

#### Baseline parameters (keep identical across runs)

When comparing consumer images, keep every variable fixed except the image tag. The sample manifests (`monovertex_sample.yaml`, `producer_sample.yaml`) are pre-configured with these values:

| Setting | Baseline | Where to set it |
|---|---|---|
| MonoVertex replicas | `1` | `spec.replicas` and `spec.scale.min`/`spec.scale.max` in `monovertex_sample.yaml` |
| Read batch size | `500` | `spec.limits.readBatchSize` in `monovertex_sample.yaml` |
| Container resources | `1500m` CPU / `640Mi` memory (requests == limits) | `spec.source.udsource.container.resources` |
| Pulsar receiver queue size | `500` (must equal `readBatchSize`) | `pulsar.consumer.consumerConfig.receiverQueueSize` in `application.yml` |
| Subscription initial position | `Earliest` | `pulsar.consumer.consumerConfig.subscriptionInitialPosition` |
| Generator load | `rpu: 10000`, `duration: 1s` | `spec.vertices[0].source.generator` in `producer_sample.yaml` |
| Pre-fill target | ~1,000,000+ messages in topic before starting consumer | Run the producer pipeline long enough |

#### Cluster setup checklist

1. **Image** — Build `apache-pulsar-java` and tag it to match `spec.source.udsource.container.image` in the MonoVertex manifest.
2. **ConfigMap** (e.g. `consumer-config`) — provides `application.yml` mounted at `/conf/application.yml`. Must include `pulsar.client`, `pulsar.consumer`, and `pulsar.admin` sections.
3. **Secret** (e.g. `pulsar-secret-cloud`) — holds Pulsar credentials referenced by `envFrom` in the MonoVertex.
4. **Pulsar topic** — same name in the consumer's `topicNames` and the producer pipeline.
5. **Producer pipeline** — deploy `producer_sample.yaml` first and let it run until the topic backlog is ~1M+ messages. This keeps the consumer metrics window stable (ingest is slower than drain, so without pre-filling the topic empties fast).
6. **Consumer MonoVertex** — deploy `monovertex_sample.yaml` once the backlog is in place.

#### Metrics stack with [numaflow-perfman](https://github.com/numaproj-labs/numaflow-perfman)

Perfman installs the Prometheus Operator, wires ServiceMonitors for pipeline and ISB metrics, and installs Grafana.

1. Clone numaflow-perfman and `make build` — binary lands at `dist/perfman`.
2. One-time install: `./dist/perfman setup -g`.
3. Port-forward (each blocks, so use separate terminals):
   - Prometheus UI: `./dist/perfman portforward -p` → `http://localhost:9090`
   - Grafana: `./dist/perfman portforward -g` → `http://localhost:3000` (default `admin`/`admin`)
4. Import the MonoVertex dashboard from this repo:
   ```bash
   ./dist/perfman dashboard --template-path development/performance-testing/dashboard-monovertex-template.json
   ```
   The command prints a link to open the dashboard in Grafana once the MonoVertex is running.

The dashboard surfaces read batch size, end-to-end latency, and forwarder metrics scoped to the MonoVertex.

#### Running a comparison

To benchmark a new consumer image against a baseline:

1. Build the new image with a distinct tag (e.g. `:experiment-foo`).
2. Update `spec.source.udsource.container.image` in the MonoVertex manifest.
3. `kubectl apply -f` the MonoVertex. Leave every other baseline parameter untouched.
4. Let metrics stabilize in Grafana over the pre-filled backlog, then record the numbers.
5. Swap the image tag back to the baseline and repeat for comparison.

### AI-Assisted Performance Testing

This repo includes a [Claude AI skill](https://github.com/numaproj-contrib/apache-pulsar-java/blob/main/development/performance-testing/CLAUDE.md) that can walk you through the entire local performance testing workflow interactively. It handles image builds, kubectl commands, perfman setup, and topic pre-filling — just provide your Pulsar cluster details and it runs everything for you.

To use it, open this repo in [Cursor](https://cursor.com/) and ask the AI agent something like:

> "Help me run a performance test for the consumer"

The agent will read the skill file, then guide you step by step — asking for your Pulsar service URL, topic name, API key, and image tag one at a time, and running all the commands on your behalf.

---

## Script Testing

Helper scripts for manually testing the producer and consumer pipelines. These live in `development/scripts/` in the repo.

### Produce Messages

**Location:** `development/scripts/produce-messages/`

A bash script that sends distinguishable messages to one or more Pulsar topics via the REST API. Useful for testing the consumer pipeline.

#### Setup

Create a `.env` file in the script directory:

```bash
export PULSAR_REST_URL="https://pc-xxxx.streamnative.aws.snio.cloud"
export PULSAR_AUTH_TOKEN="<your-token>"
export PULSAR_TENANT="demo"
export PULSAR_NAMESPACE="dev"
export TOPICS="test-topic,test-topic-2"
```

#### Usage

From the repo root:

```bash
cd development/scripts/produce-messages
chmod +x produce-messages.sh
./produce-messages.sh
```

Send more messages or to different topics:

```bash
COUNT=10 ./produce-messages.sh
TOPICS="topic-a,topic-b" ./produce-messages.sh
```

Each message body includes the topic name and a timestamp (e.g. `[test-topic] Message 1 at 2026-04-22T10:00:00Z`), so you can tell which topic each message came from in the consumer logs.

### Schema Validation Testing

**Location:** `development/scripts/schema-validation/`

Scripts for registering Avro schemas on Pulsar topics and publishing schema-encoded test messages. Useful for testing the `useAutoConsumeSchema` and `useAutoProduceSchema` features.

#### Prerequisites

- [pulsarctl](https://github.com/streamnative/pulsarctl) (`brew install pulsarctl`)
- Python 3 with `pip install avro`
- curl

#### Setup

```bash
cd development/scripts/schema-validation
cp .env.example .env
# Edit .env with your Pulsar details
```

#### Register a schema

```bash
./register-schema.sh schema-test-topic-avro.json
```

#### Publish valid messages

```bash
./publish-messages.sh schema-test-topic-avro.json
```

#### Test schema mismatch

Register one schema, then publish messages encoded with a different schema to test validation errors:

```bash
./register-schema.sh schema-test-topic-avro.json
./publish-messages.sh schema-test-topic-avro-name-only.json
```

This should cause a Java exception when the consumer tries to deserialize the mismatched messages.

#### Included schema files

| File | Description |
|---|---|
| `schema-test-topic.json` | JSON schema (TestMessage: name, topic) |
| `schema-test-topic-avro.json` | Avro schema (TestMessage: name, topic) |
| `schema-test-topic-avro-name-only.json` | Avro schema (single field: name) |
