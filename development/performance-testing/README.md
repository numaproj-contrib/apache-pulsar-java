# Performance testing — reproducible runbook

This runbook is for testing the **apache-pulsar-java** consumer in a **MonoVertex** on a **local Kubernetes** cluster, reading from **StreamNative** (or compatible) Pulsar. For comparable runs, keep **baseline parameters** identical across attempts (see below).

## What you need to create (cluster + config)

Before the numbered steps, have these in place (names below match **`monovertex_sample.yaml`**; if you rename resources, update the MonoVertex spec to match). Bullets marked **Baselines:** should stay aligned with the [baseline parameters](#baseline-parameters-keep-these-the-same-for-fair-comparisons) when you compare runs.

- **Apache Pulsar topic** — Create a topic on your Pulsar cluster. Your **producer** writes to it and your **consumer** reads from it, so keep the topic name consistent in both configs.

- **ConfigMap** (e.g. `consumer-config`) — Provide an **`application.yml`** entry with the consumer configuration, and mount it at **`/conf/application.yml`** via the MonoVertex volume. **Baselines:** keep consumer **receiver queue `500`** so it matches MonoVertex **`readBatchSize: 500`**.

  > **Config format changed after Spring removal:** Images after spring rm use a plain `pulsar` root key in `application.yml` (e.g. `pulsar.client.clientConfig`). Older images built before Spring was removed expect the `spring.pulsar` root key (e.g. `spring.pulsar.client.clientConfig`). If the pod fails with config-related errors, check which format your image expects and update `application.yml` accordingly. See `development/testing/` for example configs in the current (post-Spring) format.

- **Secret** (e.g. `pulsar-secret-cloud`) — Contains the credentials needed to authenticate with Pulsar. If the consumer and producer use the same API key, a single Secret is enough—just reference the same name in both the MonoVertex and producer pipeline manifests. See `docs/get-started` for the secret template.

- **Container image** — Build the **apache-pulsar-java** container image and tag it to match **`spec.source.udsource.container.image`** in your MonoVertex YAML. **Baselines:** keep **`resources`** at **1500m CPU / 640Mi memory** on the MonoVertex unless you intentionally change capacity.

- **Load generator** — A producer pipeline (see **`producer_sample.yaml`**). Since you are comparing consumer images, keep the producer side fixed across runs. **Baselines:** **`rpu: 10000`**, **`duration: 1s`**, and the same message body/payload.

- **Consumer MonoVertex** — Create a spec like **`monovertex_sample.yaml`** and make sure it references the same ConfigMap, Secret, and image tag you created above. **Baselines / match the sample for fair comparison:** **`spec.replicas: 1`**, **`spec.scale` min/max `1`**, **`spec.limits.readBatchSize: 500`**, and **`spec.source.udsource.container.resources`** (`1500m` CPU / `640Mi` memory).

## Baseline parameters (keep these the same for fair comparisons)

- **MonoVertex replicas:** `1`. Set in `monovertex_sample.yaml` on `spec.replicas` and on `spec.scale` min/max.
- **Read batch size:** `500`. Set in `monovertex_sample.yaml` as `spec.limits.readBatchSize`.
- **Container CPU / memory:** `1500m` CPU, `640Mi` memory (both requests and limits). Set on `spec.source.udsource.container.resources`.
- **Pulsar consumer receiver queue:** `500` in your consumer `application.yml`, aligned with batch size (for why, see [Pulsar PR #22619](https://github.com/apache/pulsar/pull/22619)).
- **Generator load reference:** `rpu: 10000`, `duration: 1s` in `producer_sample.yaml` under `spec.vertices[0].source.generator`.
- **Subscription initial position:** `Earliest` in your consumer `application.yml`, so the consumer reads from the beginning of the backlog rather than only new messages.
- **Pre-fill duration:** `500s` — how long the producer runs before the consumer starts, to build up a backlog of at least ~4,000,000 messages.
- **Measurement duration:** `90s` — how long the consumer metrics are captured after warmup.

## Files in this folder

`monovertex_sample.yaml` and `producer_sample.yaml` are **reference templates** — do not edit them directly. Instead, copy them to create your own manifests (e.g. `my-consumer-mvtx.yaml`, `my-producer-pipeline.yaml`) and adjust the image tag, ConfigMap/Secret names, topic, etc. to match your setup. This keeps the samples stable for future comparisons.

- **`monovertex_sample.yaml`** — Consumer MonoVertex (UD source + `log` sink).
- **`producer_sample.yaml`** — Reference pipeline (generator → UD sink); use generator specs for comparable load generation.
- **`dashboard-monovertex-template.json`** — Grafana dashboard for MonoVertex metrics (import via [numaflow-perfman](https://github.com/numaproj-labs/numaflow-perfman) `dashboard --template-path`, see step 4).

## Steps 

### 1. Build and load the apache-pulsar-java image

Build the container image and **tag** it so it matches **`spec.source.udsource.container.image`** in your MonoVertex YAML. If you change the tag, update the manifest. **Baseline check:** if you edit `monovertex_sample.yaml`, keep **`spec.source.udsource.container.resources`** at **1500m CPU / 640Mi memory** unless you are deliberately testing different resources.

### 2. Wire Pulsar + Numaflow configs (must match baselines)

- **Create** the **ConfigMap** and **Secret** the MonoVertex references (`volumes` / `envFrom` in `monovertex_sample.yaml`). The ConfigMap should provide **`application.yml`** for the consumer, and the Secret should provide the Pulsar credentials. Names are examples; keep the manifest and cluster objects in sync.
- **Baseline:** set the Pulsar consumer **receiver queue size to `500`** in `pulsar.consumer.consumerConfig` so it stays aligned with **`spec.limits.readBatchSize: 500`** in the MonoVertex (see [Pulsar PR #22619](https://github.com/apache/pulsar/pull/22619)).

### 3. Pre-fill the topic

The generator in the producer sample can be **slower** than the MonoVertex consumer. If ingest is slower than drain, the topic empties quickly and the consumer metrics are only shown for a brief period.

- Run your producer until the topic has **at least ~1,000,000 messages** so you get a steady stream of consumer metrics.
- If you use **`producer_sample.yaml`**, keep the generator at the baseline **`rpu` / `duration`** unless you are intentionally changing load.

### 4. Metrics stack with [numaflow-perfman](https://github.com/numaproj-labs/numaflow-perfman)

Perfman installs the **Prometheus Operator**, wires **ServiceMonitors** for pipeline/ISB metrics, and installs **Grafana**. It scrapes metrics from your MonoVertex (and related objects) and you view them in Grafana.

**Build or run perfman**

- From numaflow-perfman: clone the repo, `make build`, the binary will be at `dist/perfman`.

**One-time (or when you need a clean metrics plane)**

- Run `./dist/perfman setup -g`. This deploys Prometheus Operator and creates the monitors used for scraping, and perfman **installs Grafana** as part of setup.

**Port-forwards (blocking — use another tab/window)**

- Prometheus UI: `./dist/perfman portforward -p` → typically **http://localhost:9090**
- `./dist/perfman portforward -g` → **http://localhost:3000** (default login is often `admin` / `admin`).

**Import this repo’s MonoVertex dashboard**

- From the **apache-pulsar-java** repo root (adjust path if needed). After the MonoVertex is running on the cluster, this prints a link to open the dashboard in Grafana:

  `./dist/perfman dashboard --template-path development/performance-testing/dashboard-monovertex-template.json`

### 5. Deploy the MonoVertex

**Baseline check before `kubectl apply`:** in your MonoVertex manifest, confirm **`spec.replicas`**, **`spec.scale` min/max**, **`spec.limits.readBatchSize`**, and **`resources.requests`** still match the [baseline parameters](#baseline-parameters-keep-these-the-same-for-fair-comparisons) (sample file is already set to those values).

```bash
kubectl apply -f development/performance-testing/monovertex_sample.yaml
```

(Use your path; fix namespace with `-n` if needed.)

Wait until pods are ready. The dashboard should show **read batch size**, **e2e latency**, and related forwarder metrics for that MonoVertex.

To test a new consumer image, simply build another image, tag it differently, update **`spec.source.udsource.container.image`** in the MonoVertex YAML, and redeploy.
