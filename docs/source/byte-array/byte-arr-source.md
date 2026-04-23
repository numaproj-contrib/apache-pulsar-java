# Byte Array Source (Consumer)

This guide walks you through consuming raw byte array messages from a Pulsar topic into a Numaflow pipeline.

The example builds a pipeline that reads from a Pulsar topic and prints received messages using the built-in Numaflow [log sink](https://numaflow.numaproj.io/user-guide/sinks/log/).

---
## How the consumer reads and acks

The consumer follows a **strict read → ack cycle with no read ahead**. Numaflow calls `read()` and `ack()` on the source in alternating fashion, and this consumer enforces that pattern tightly:

- **No readahead.** `read()` returns immediately without pulling a new batch from Pulsar if the previous batch has not yet been fully acknowledged. A new batch is only fetched once `messagesToAck` is empty.
- **Strict ack matching.** `ack()` compares the offsets in the request against the set of pending message IDs from the last read. If they don't match exactly (missing IDs, extra IDs, reordered in a way that changes the set), the ack is **skipped** and logged as an error — no partial acks. Pulsar will eventually redeliver those messages.
- **No reack of already-acked messages.** Because `messagesToAck` is cleared after a successful ack, a repeated ack for the same batch is treated as a mismatch and ignored.

This design keeps the consumer's state simple: at any moment, either a batch is in flight waiting to be acked, or nothing is pending and the next read can proceed. There is no overlap between batches and no prefetch beyond Pulsar's own `receiverQueueSize`.

---

## Prerequisites

- A running Pulsar cluster — run one locally with `docker-compose up` (see the [Home](../../index.md) page) or deploy on [StreamNative](../../get-started/pulsar-on-streamnative.md).
- If you want a partitioned topic, create it before deploying the pipeline.

---

## 1. Create the ConfigMap

The ConfigMap holds `application.yml`, which configures the Pulsar client, consumer, and admin.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pulsar-config
data:
  application.yml: |
    pulsar:
      client:
        clientConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud"
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
          # Example: OAuth2 - authParams: "${PULSAR_OAUTH_CLIENT_SECRET}"
      consumer:
        enabled: true
        useAutoConsumeSchema: true
        consumerConfig:
          # Single topic (string) or multiple topics (comma-separated string)
          topicNames: "persistent://public/default/test-topic"
          # topicNames: "persistent://public/default/topic-a, persistent://public/default/topic-b"
          subscriptionName: "test-subscription"
      admin:
        adminConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud"
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
```

!!! info "Both `client` and `admin` are required"
    The application uses the Pulsar **client** to consume messages and the Pulsar **admin** API to inspect topics (partition counts, subscription backlog). Both sections must be present in the ConfigMap — omitting `admin` will cause the consumer to fail on startup.

!!! info "How `${PULSAR_AUTH_TOKEN}` works"
    `${PULSAR_AUTH_TOKEN}` is **not** resolved by Kubernetes — it's resolved by the application at runtime. The Pipeline spec uses `envFrom` to inject Secret keys as env vars into the container. When the app reads `application.yml`, it substitutes `${PULSAR_AUTH_TOKEN}` with the env value.

!!! note "Why `authParams` and not `authParamMap`?"
    Pulsar generally supports both `authParams` (single string) and `authParamMap` (key/value map) for auth plugins. For `AuthenticationToken` specifically, only the string form works — the class has no constructor that accepts a `Map`, so `authParamMap` will fail at startup. See the [AuthenticationToken source](https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/auth/AuthenticationToken.java).

### Key fields

The table below highlights the most common fields. For the full list of accepted keys under each section, see the official Pulsar docs:

- `clientConfig` → [all client configurations](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client)
- `consumerConfig` → [all consumer configurations](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-consumer)
- `adminConfig` → accepts the same keys as `clientConfig`

| Field | Required | Notes |
|---|---|---|
| `client.clientConfig.serviceUrl` | yes | The broker URL. |
| `consumer.consumerConfig.topicNames` | yes | Single topic as a string, or multiple topics as a **comma-separated string** (e.g. `"persistent://public/default/topic-a, persistent://public/default/topic-b"`). |
| `consumer.consumerConfig.subscriptionName` | no | Defaults to `{PipelineName}-{VertexName}-sub`. |
| `consumer.consumerConfig.subscriptionInitialPosition` | no | `Earliest` or `Latest`. Defaults to `Latest` — use `Earliest` to replay messages produced before the subscription existed. |
| `admin.adminConfig.serviceUrl` | yes | The admin (web) URL. For **StreamNative Cloud** and most managed clusters, this is the same HTTPS URL as `clientConfig.serviceUrl`. For self-hosted Pulsar, it's typically a separate URL (e.g. `http://broker:8080` vs `pulsar://broker:6650`). |

!!! tip "Object types in the ConfigMap"
    Pulsar types that are Enums can be passed as strings. Other complex types need YAML List/Map support — check the Pulsar docs if a field isn't behaving as expected.

!!! warning "Keep `receiverQueueSize` equal to `readBatchSize`"
    The Pulsar consumer maintains a local prefetch buffer (`receiverQueueSize`) separate from Numaflow's `readBatchSize`. When these two values differ, behavior changes across Pulsar client versions due to [apache/pulsar#22619](https://github.com/apache/pulsar/pull/22619):

    - **Pulsar client 3.x** — the smaller `receiverQueueSize` silently overrides a larger `readBatchSize`.
    - **Pulsar client 4.x+** — the larger `readBatchSize` rewrites `receiverQueueSize` to match.

    To avoid version-dependent surprises, always set `consumer.consumerConfig.receiverQueueSize` equal to the pipeline's `limits.readBatchSize`.

---

## 2. Create the Secret

Create a Kubernetes Secret with your Pulsar credentials. The ConfigMap references these via `${PULSAR_AUTH_TOKEN}`.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pulsar-consumer-auth-secret
type: Opaque
stringData:
  # API Token Authentication (StreamNative Cloud, DataStax Astra, self-hosted with tokens)
  PULSAR_AUTH_TOKEN: "YOUR-API-KEY-HERE"

  # Example: OAuth2 client secret
  # PULSAR_OAUTH_CLIENT_SECRET: "your-oauth-client-secret"
```

!!! note "Local clusters without auth"
    For local Pulsar clusters without authentication, skip the Secret and remove `authPluginClassName`, `authParams`, and `envFrom` from the ConfigMap and Pipeline.

---

## 3. Create the Pipeline

Update the `image` field to the version you want (from [Quay.io tags](https://quay.io/repository/numaio/numaflow-java/pulsar-java?tab=tags) or a locally built image). Make sure the `args` under the consumer vertex match the file path in the ConfigMap.

```yaml
apiVersion: numaflow.numaproj.io/v1alpha1
kind: Pipeline
metadata:
  name: raw-consumer-pipeline
spec:
  limits:
    readBatchSize: 1
  vertices:
    - name: in
      scale:
        min: 1
      volumes:
        - name: pulsar-config-volume
          configMap:
            name: pulsar-config
            items:
              - key: application.yml
                path: application.yml
      source:
        udsource:
          container:
            image: apache-pulsar-java:v0.3.0
            args: ["--config=file:/conf/application.yml"]
            imagePullPolicy: Never
            env:
              # Uncomment to enable debug-level per-message logs
              # - name: LOGGING_LEVEL_IO_NUMAPROJ_PULSAR
              #   value: "DEBUG"
              # Uncomment to switch from JSON to plain-text log output
              # - name: NUMAFLOW_DEBUG
              #   value: "true"
            envFrom:
              - secretRef:
                  name: pulsar-consumer-auth-secret
            volumeMounts:
              - name: pulsar-config-volume
                mountPath: /conf
    - name: out
      scale:
        min: 1
      sink:
        log: {}
  edges:
    - from: in
      to: out
```

---

## 4. Observe the messages

Wait for the pipeline to be up and running, then produce messages to the topic. You should see them printed in the log sink.
