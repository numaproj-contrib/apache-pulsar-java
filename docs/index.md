# Apache Pulsar Java

Apache Pulsar user-defined source and sink for [Numaflow](https://numaflow.numaproj.io/), implemented in Java.

This project provides a single container image that can run as either:

- A **Numaflow user-defined source** (consumer) — reads messages from a Pulsar topic and feeds them into a Numaflow pipeline
- A **Numaflow user-defined sink** (producer) — takes messages from a Numaflow pipeline and publishes them to a Pulsar topic

The mode is determined by your `application.yml` configuration (`pulsar.consumer.enabled` or `pulsar.producer.enabled`).

## Quick Start

### Prerequisites

- A Kubernetes cluster with [Numaflow installed](https://numaflow.numaproj.io/quick-start/)
- A Pulsar cluster (local via Docker Compose, or managed via [StreamNative](get-started/pulsar-on-streamnative.md))
- Java 23 (for building from source)

### 1. Set Up a Pulsar Cluster

You need a running Pulsar cluster before deploying the source or sink.

=== "Local (Docker Compose)"

    Run a Pulsar broker and Pulsar Manager UI:

    ```bash
    docker-compose up
    ```

    Then set up the Pulsar Manager admin account:

    ```bash
    CSRF_TOKEN=$(curl http://localhost:7750/pulsar-manager/csrf-token)
    curl \
      -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
      -H "Cookie: XSRF-TOKEN=$CSRF_TOKEN;" \
      -H "Content-Type: application/json" \
      -X PUT http://localhost:7750/pulsar-manager/users/superuser \
      -d '{"name": "admin", "password": "apachepulsar", "description": "test", "email": "username@test.org"}'
    ```

    Access Pulsar Manager at [http://localhost:9527](http://localhost:9527) and create an environment with service URL `http://pulsar:8080/`.

    ![Pulsar Manager environment setup](assets/pulsar-manager-env.png)

=== "StreamNative Cloud"

    For managed Pulsar clusters on StreamNative Cloud, follow the full setup guide:

    **[Pulsar on StreamNative →](get-started/pulsar-on-streamnative.md)**

    This covers creating a cluster, getting your service URL, API key authentication, and wiring up ConfigMaps and Secrets for both producer and consumer.

### 2. Deploy to Numaflow

Once your Pulsar cluster is running, build and deploy:

```bash
# Build the project and create the Docker image
mvn clean package

# Deploy the configuration and pipeline to Kubernetes
kubectl apply -f <path-to-config-map.yaml>
kubectl apply -f <path-to-pipeline.yaml>

# (Optional) Port-forward to access Numaflow UI
kubectl -n numaflow-system port-forward deployment/numaflow-server 8443:8443
```

Optionally, access the Numaflow UI at [https://localhost:8443/](https://localhost:8443/) to monitor your pipeline.

See the [Source (Consumer)](source/byte-array/byte-arr-source.md) and [Sink (Producer)](sink/byte-array/byte-arr-sink.md) guides for full ConfigMap and pipeline examples.
