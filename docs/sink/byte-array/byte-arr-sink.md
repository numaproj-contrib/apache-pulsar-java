# Byte Array Sink (Producer)

This guide walks you through publishing byte array messages to a Pulsar topic from a Numaflow pipeline.

The example builds a pipeline that reads from the built-in [generator source](https://numaflow.numaproj.io/user-guide/sources/generator/) and writes messages to the target topic `persistent://public/default/test-topic`.

---

## Prerequisites

- A running Pulsar cluster — run one locally with `docker-compose up` (see the [Home](../../index.md) page) or deploy on [StreamNative](../../get-started/pulsar-on-streamnative.md).

---

## 1. Create the ConfigMap

The ConfigMap holds `application.yml`, which configures the Pulsar client, producer, and admin.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: byte-arr-producer-config
data:
  application.yml: |
    pulsar:
      client:
        clientConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud"
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
          # Example: OAuth2 - authParams: "${PULSAR_OAUTH_CLIENT_SECRET}"
      producer:
        enabled: true
        # Use broker schema to validate payloads; when false, uses Schema.BYTES (no validation).
        useAutoProduceSchema: true
        # When true, drop messages that fail schema/serialization and continue publishing.
        dropInvalidMessages: false
        producerConfig:
          topicName: "persistent://public/default/test-topic"
      admin:
        adminConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud"
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
```

!!! info "Both `client` and `admin` are required"
    The application uses the Pulsar **client** to publish messages and the Pulsar **admin** API to validate that the target topic exists before starting. Both sections must be present in the ConfigMap — omitting `admin` will cause the producer to fail on startup.

!!! info "How `${PULSAR_AUTH_TOKEN}` works"
    `${PULSAR_AUTH_TOKEN}` is **not** resolved by Kubernetes — it's resolved by the application at runtime. The Pipeline spec uses `envFrom` to inject Secret keys as env vars into the container. When the app reads `application.yml`, it substitutes `${PULSAR_AUTH_TOKEN}` with the env value.

!!! note "Why `authParams` and not `authParamMap`?"
    Pulsar generally supports both `authParams` (single string) and `authParamMap` (key/value map) for auth plugins. For `AuthenticationToken` specifically, only the string form works — the class has no constructor that accepts a `Map`, so `authParamMap` will fail at startup. See the [AuthenticationToken source](https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/auth/AuthenticationToken.java).

### Key fields

The table below highlights the most common fields. For the full list of accepted keys under each section, see the official Pulsar docs:

- `clientConfig` → [all client configurations](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client)
- `producerConfig` → [all producer configurations](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-producer)
- `adminConfig` → accepts the same keys as `clientConfig`

| Field | Required | Notes |
|---|---|---|
| `client.clientConfig.serviceUrl` | yes | The broker URL. |
| `producer.producerConfig.topicName` | yes | Target topic. **Must already exist** — the producer validates this on startup and fails fast if the topic is missing. |
| `producer.producerConfig.producerName` | — | Do **not** set — the image overwrites it with the pod name. |
| `producer.useAutoProduceSchema` | no | When `true`, uses the broker schema to validate payloads. When `false`, uses `Schema.BYTES` (no validation). |
| `producer.dropInvalidMessages` | no | When `true`, drops messages that fail schema/serialization and keeps publishing. |
| `admin.adminConfig.serviceUrl` | yes | The admin (web) URL. For **StreamNative Cloud** and most managed clusters, this is the same HTTPS URL as `clientConfig.serviceUrl`. For self-hosted Pulsar, it's typically a separate URL (e.g. `http://broker:8080` vs `pulsar://broker:6650`). |

!!! warning "Create the topic first"
    Unlike the consumer, the producer will **not** auto-create the target topic. Create it in Pulsar Manager (local) or the StreamNative Cloud console before deploying the pipeline — otherwise the pod will crash on startup.

---

## 2. Create the Secret

Create a Kubernetes Secret with your Pulsar credentials. The ConfigMap references these via `${PULSAR_AUTH_TOKEN}`.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pulsar-producer-auth-secret
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

Update the `image` field to the version you want (from [Quay.io tags](https://quay.io/repository/numaio/numaflow-java/pulsar-java?tab=tags) or a locally built image). Make sure the `args` under the sink vertex match the file path in the ConfigMap.

```yaml
apiVersion: numaflow.numaproj.io/v1alpha1
kind: Pipeline
metadata:
  name: raw-producer-pipeline
spec:
  vertices:
    - name: in
      source:
        generator:
          rpu: 1
          duration: 10s
          msgSize: 10
    - name: out
      volumes:
        - name: pulsar-config-volume
          configMap:
            name: byte-arr-producer-config
            items:
              - key: application.yml
                path: application.yml
      sink:
        udsink:
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
                  name: pulsar-producer-auth-secret
            volumeMounts:
              - name: pulsar-config-volume
                mountPath: /conf
  edges:
    - from: in
      to: out
```

---

## 4. Observe the messages

Wait for the pipeline to be up and running. Messages will be published to the topic configured in `producerConfig.topicName`.
