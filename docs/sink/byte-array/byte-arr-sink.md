# Publish messages to a topic

### Introduction

This document demonstrates how to use `apache-pulsar-java` to publish byte array messages to a topic directly


### Example

In this example, we create a pipeline that reads from the
builtin [generator source](https://numaflow.numaproj.io/user-guide/sources/generator/) and writes the messages to a
target topic `persistent://public/default/test-topic`. 

#### Pre-requisite

Have a Pulsar cluster running. 
If you don't have a Pulsar cluster running, you can either run one locally with `docker-compose up` (see the [Home](../../index.md) page) or deploy one on [StreamNative](../../get-started/pulsar-on-streamnative.md).

#### Configure the Pulsar producer

Create a ConfigMap with the producer configuration:

```yaml
# API Key example
apiVersion: v1
kind: ConfigMap
metadata:
  name: byte-arr-producer-config
data:
  application.yml: |
    pulsar:
      client: # see here for all configurations: https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client
        clientConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud" # Example HTTPS URL
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
        adminConfig: # Accepts the same key-value pair configurations as pulsar client
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud" # Example HTTPS URL
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
```

In the ConfigMap:

* `clientConfig` allows you to configure the pulsar client. See all available configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client): 
    * serviceUrl must be specified as it is a required field 

* `producerConfig` allows you to set producer configurations for the pulsar client. See all available configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-producer): 
    * `topicName` is the Pulsar topic name to write data to, and is a required field and therefore must be in the ConfigMap
    * `producerName` should NOT be specified. If it is specified, the image will overwrite the inputted name

Deploy the ConfigMap to the Kubernetes cluster.

#### Create the pipeline

Create the pipeline using the ConfigMap from the previous step. Make sure that the args list under the sink vertex matches the file paths in the ConfigMap.

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
                  name: pulsar-producer-auth-secret # All secret keys injected as env vars
            volumeMounts:
              - name: pulsar-config-volume
                mountPath: /conf
  edges:
    - from: in
      to: out
```

#### Observe the messages
Wait for the pipeline to be up and running. You can observe the messages in the `test-config-topic` topic. 
