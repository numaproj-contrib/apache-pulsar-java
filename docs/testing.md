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

For more in-depth testing on your own cluster, see the [performance testing runbook](https://github.com/numaproj-contrib/apache-pulsar-java/tree/main/development/performance-testing).

??? note "What the local runbook covers"
    - Baseline parameters for fair comparison (replicas, batch size, CPU/memory, receiver queue)
    - Setting up metrics with [numaflow-perfman](https://github.com/numaproj-labs/numaflow-perfman) (Prometheus + Grafana)
    - Pre-filling topics and deploying the consumer MonoVertex
    - Importing the Grafana dashboard for MonoVertex metrics

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
