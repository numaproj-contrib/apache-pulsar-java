# Performance Testing — Consumer MonoVertex

When asked to performance test the apache-pulsar-java consumer, follow these steps. Refer to `development/performance-testing/README.md` for full details and baseline parameters.

## Prerequisites

- Local Kubernetes cluster (e.g. Rancher Desktop / k3s / minikube)
- Numaflow installed on the cluster (`numaflow-system` namespace)
- Access to a Pulsar cluster with a topic created
- `numaflow-perfman` cloned and built (`make build` → `dist/perfman`)

## Gathering config from the user

Ask for configuration values **one at a time**, not all at once. For example:
1. Pulsar service URL (e.g. `pulsar+ssl://pc-abc123.streamnative.cloud:6651` or `https://pc-abc123.streamnative.cloud`)
2. Topic name — fully qualified: `persistent://<tenant>/<namespace>/<topic>` (e.g. `persistent://public/default/test-topic`)
3. Subscription name (e.g. `my-consumer-sub`)
4. Create the Kubernetes Secret YAML file with a placeholder token, then tell the user the file path so they can paste in their real token before applying. Never ask for the auth token directly — it should not appear in chat logs.
5. Image tag (e.g. `apache-pulsar-java:v0.3.0`, `apache-pulsar-java:after-spring-rm`)
6. Path to the `numaflow-perfman` repo. Check if it exists at `../numaflow-perfman` (sibling directory) first. If not found, ask the user where it is or whether to clone it into the parent directory (`../`).

Wait for each answer before asking the next question. Run all commands (build, kubectl apply, perfman, etc.) on behalf of the user — do not ask them to run commands manually. Always confirm with the user before executing each command. When confirming, briefly describe what the command does (e.g. "This builds the Docker image and tags it as X. Shall I run this?").

## File organization

Place all generated YAML files (ConfigMaps, Secrets, MonoVertex specs, producer pipelines) in `development/performance-testing/running-configs/`. Do not put them alongside the sample templates.

## Quick workflow

### 1. Build the image

Jib is bound to the `package` phase in `pom.xml`, so `mvn clean package` builds the Docker image automatically. Use `-Djib.to.image` to set the tag:

```bash
mvn clean package -Djib.to.image=apache-pulsar-java:<tag>
```

Update `spec.source.udsource.container.image` in your MonoVertex YAML to match the tag.

### 2. Create ConfigMap, Secret, and manifests

- **ConfigMap**: must contain `application.yml` with the consumer config. Before generating configs, read the reference examples in `docs/` for the correct YAML structure and include ALL fields (admin section, enabled, useAutoConsumeSchema, etc.):
  - Consumer: `docs/source/byte-array/manifests/api-key/byte-arr-consumer-config.yaml`
  - Consumer secret: `docs/source/byte-array/manifests/api-key/byte-arr-consumer-secret.yaml`
  - Producer: `docs/sink/byte-array/manifests/api-key/byte-arr-producer-config.yaml`
  - Producer secret: `docs/sink/byte-array/manifests/api-key/byte-arr-producer-secret.yaml`
- **Secret**: must contain `PULSAR_AUTH_TOKEN`. One secret can serve both consumer and producer if they share credentials.
- **MonoVertex manifest**: copy `development/performance-testing/monovertex_sample.yaml`, update image tag, ConfigMap/Secret names, and topic. Do not alter baseline parameters (replicas: 1, readBatchSize: 500, CPU: 100m, memory: 128Mi).
- **Producer manifest**: copy `development/performance-testing/producer_sample.yaml`, update image tag, ConfigMap/Secret names, and topic.

After generating the files, apply only the ConfigMap(s), Secret, and producer pipeline — do **not** apply the consumer MonoVertex yet (that happens in step 5):
```bash
kubectl apply -f development/performance-testing/running-configs/<consumer-config>.yaml
kubectl apply -f development/performance-testing/running-configs/<producer-config>.yaml
kubectl apply -f development/performance-testing/running-configs/<secret>.yaml
```

### 3. Pre-fill the topic

Deploy the producer pipeline to write messages:
```bash
kubectl apply -f development/performance-testing/running-configs/<producer-pipeline>.yaml
```

Ask the user whether to wait for ~1,000,000 messages (recommended for steady metrics) or skip ahead and deploy the consumer immediately. If skipping, the consumer may drain the topic quickly and metrics will only be visible briefly.

### 4. Set up metrics (one-time)

Use the perfman path gathered earlier. If perfman hasn't been built yet, run `make build` in the perfman repo first.

```bash
<perfman-path>/dist/perfman setup -g
```
This installs Prometheus Operator, ServiceMonitors, and Grafana on the cluster.

Port-forward Grafana and Prometheus (each in a separate terminal):
```bash
<perfman-path>/dist/perfman portforward -g   # Opens Grafana at localhost:3000
<perfman-path>/dist/perfman portforward -p   # Opens Prometheus at localhost:9090
```

Import the dashboard (after monovertex)
```bash
<perfman-path>/dist/perfman dashboard --template-path development/performance-testing/dashboard-monovertex-template.json
```
This creates a Grafana dashboard for MonoVertex metrics and prints a link to open it. Mention the username (admin) and password (admin) so user knows how to login to grafana.

### 5. Deploy the consumer MonoVertex

```bash
kubectl apply -f development/performance-testing/running-configs/<monovertex>.yaml
```

Watch the Grafana dashboard for read batch size, e2e latency, and forwarder metrics.

### 6. Next steps

After observing metrics, ask the user whether to:

**A) Test another image** — rebuild with a new tag, update the MonoVertex manifest's image field, and redeploy:
```bash
mvn clean package -Djib.to.image=apache-pulsar-java:<new-tag>
kubectl delete -f development/performance-testing/running-configs/<monovertex>.yaml
kubectl apply -f development/performance-testing/running-configs/<monovertex>.yaml
```
Keep baseline parameters identical for fair comparison.

**B) Tear down** — remove all test resources and metrics stack from the cluster:
```bash
# Delete test workloads and configs
kubectl delete -f development/performance-testing/running-configs/<monovertex>.yaml
kubectl delete -f development/performance-testing/running-configs/<producer-pipeline>.yaml
kubectl delete -f development/performance-testing/running-configs/<consumer-config>.yaml
kubectl delete -f development/performance-testing/running-configs/<producer-config>.yaml
kubectl delete -f development/performance-testing/running-configs/<secret>.yaml

# Tear down perfman metrics stack (Prometheus, Grafana, ServiceMonitors)
<perfman-path>/dist/perfman clean
```
Ask the user whether to also tear down the perfman stack or keep it for future runs.

## Baseline parameters (do not change for fair comparison)

| Parameter | Value | Where |
|---|---|---|
| MonoVertex replicas | 1 | `spec.replicas`, `spec.scale` min/max |
| Read batch size | 500 | `spec.limits.readBatchSize` |
| Container CPU/memory | 100m / 128Mi | `spec.source.udsource.container.resources.requests` |
| Pulsar receiver queue | 500 | `application.yml` → `pulsar.consumer.consumerConfig.receiverQueueSize` |
| Generator load | rpu: 10000, duration: 1s | producer pipeline `spec.vertices[0].source.generator` |

## Key files

- `development/performance-testing/README.md` — full runbook
- `development/performance-testing/monovertex_sample.yaml` — consumer MonoVertex template (do not edit directly, copy first)
- `development/performance-testing/producer_sample.yaml` — producer pipeline template (do not edit directly, copy first)
- `development/performance-testing/dashboard-monovertex-template.json` — Grafana dashboard template
