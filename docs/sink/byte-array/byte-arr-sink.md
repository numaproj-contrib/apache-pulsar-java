# Publish messages to a topic

### Introduction

This document demonstrates how to use `apache-pulsar-java` to publish byte array messages to a topic directly


### Example

In this example, we create a pipeline that reads from the
builtin [generator source](https://numaflow.numaproj.io/user-guide/sources/generator/) and writes the messages to a
target topic `test-config-topic`. 

#### Pre-requisite

Have a Pulsar cluster running.

#### Configure the Kafka producer

Use the example [ConfigMap](manifests/byte-arr-producer-config.yaml) to configure the Pulsar sink.

In the ConfigMap:

* `clientConfig` allows you to configure the client. See all avalaible configurations [here:](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client) 
    *    serviceUrl must be specified as it is a required field 

* `producerConfig` allows you to configure the client. See all avalaible configurations [here:](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-producer) 
    * `topicName` is the Pulsar topic name to write data to, and is a required field and therefore must be in the ConfigMap
    * `producerName` should NOT be specified. If it is specified, the image will overwrite the inputted name

Deploy the ConfigMap to the Kubernetes cluster.

#### Create the pipeline

Use the example [pipeline](manifests/byte-arr-producer-pipeline.yaml) to create the pipeline, using the ConfigMap created in
the previous step. Please make sure that the args list under the sink vertex matches the file paths in the ConfigMap.

#### Observe the messages
Wait for the pipeline to be up and running. You can observe the messages in the `test-config-topic` topic. 
If you have multiple pods running for a vertex, you should see multiple producers for a given topic name with the name of each respective pod. 

You can use the [pulsar-admin CLI](https://pulsar.apache.org/docs/4.0.x/get-started-pulsar-admin/) to check the messages in the topic. 