# Consume messages from a topic

### Introduction

This document demonstrates how to use `apache-pulsar-java` to consume raw byte array messages from a topic.


### Example

In this example, we create a pipeline that reads from Apache Pulsar from the specified topic in the config map, and uses the built-in Numaflow [log sink](https://numaflow.numaproj.io/user-guide/sinks/log/) to print all received messages.

#### Pre-requisite

Have a Pulsar cluster running and if you want a partitioned topic, you must create it before.
If you don't have a Pulsar cluster running, you can either run one locally with `docker-compose up` (see the [Home](../../index.md) page) or deploy one on [StreamNative](../../get-started/pulsar-on-streamnative.md).

#### Configure the Pulsar consumer

Create a ConfigMap with the consumer configuration. Make sure that you have the consumer enabled.

```yaml
# API Key example
apiVersion: v1
kind: ConfigMap
metadata:
  name: pulsar-config
data:
  application.yml: |
    pulsar:
      client: # see here for all configurations: https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client
        clientConfig:
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud" # Example HTTPS URL
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
          # Example: OAuth2 - authParams: "${PULSAR_OAUTH_CLIENT_SECRET}"
      consumer: # see here for all configurations: https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-consumer
        enabled: true
        useAutoConsumeSchema: true
        consumerConfig:
          # Single topic (string) or multiple topics (comma-separated string)
          topicNames: "persistent://public/default/test-topic"
          # topicNames: "persistent://public/default/topic-a, persistent://public/default/topic-b"
          subscriptionName: "test-subscription"
      admin:
        adminConfig: # Accepts the same key-value pair configurations as pulsar client
          serviceUrl: "https://pc-xxxxx.streamnative.aws.snio.cloud" # Example HTTPS URL
          authPluginClassName: org.apache.pulsar.client.impl.auth.AuthenticationToken
          authParams: "${PULSAR_AUTH_TOKEN}"
```

In the ConfigMap:

* `clientConfig` allows you to configure the pulsar client. See all available configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client):
    * serviceUrl must be specified as it is a required field. This is the brokerServiceUrl.

* `consumerConfig` allows you to set consumer configurations for the pulsar client. Below are some common configurations to consider specifying. See all available configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-consumer):
    * `topicNames` is the Pulsar topic name to write data to, and is a required field. It must be in the ConfigMap. Currently, we only support 1 topic therefore only one **string** value is accepted even though the Pulsar docs indicate that topicNames is of type Set. 
    * If a `subscriptionName` is not specified, the image will give it a default value of `{PipelineName}-{VertexName}-sub`
    * `subscriptionInitialPosition` is the initial position of the subscription. It can be `Earliest` or `Latest`. If it is not specified, Pulsar defaults to Latest meaning that the subscription will start consuming messages from the latest available message in the topic. So if messages were produced to a topic before the subscription, they will not be consumered. If you want to start consuming messages from the earliest available message, you can specify `Earliest`.
* `adminConfig` must be specified in order for the consumer to work. The serviceUrl is a required field but note that this is **different** from the serviceUrl file in the clientConfig. This is the webServiceUrl.
* NOTE: To verify if the configMap supports the object Type, look through Pulsar docs to see if that object is an Enum. If it is an Enum, you can provide the value as a String in the config map. Otherwise, check if .yaml files support that type (ex. yaml files support List and Maps). 

#### Create the pipeline

Create the pipeline using the ConfigMap from the previous step. Make sure that the args list under the consumer vertex matches the file paths in the ConfigMap.

```yaml
apiVersion: numaflow.numaproj.io/v1alpha1
kind: Pipeline
metadata:
  name: raw-consumer-pipeline
spec:
  limits:
    readBatchSize: 1 # Change if you want a different batch size
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
                  name: pulsar-consumer-auth-secret # All secret keys injected as env vars
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

#### Observe the messages
Wait for the pipeline to be up and running. Produce messages to the specified topic and verify that messages are printed in the log sink.
