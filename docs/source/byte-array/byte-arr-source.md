# Consume messages from a topic

### Introduction

This document demonstrates how to use `apache-pulsar-java` to consume raw byte array messages from a topic.


### Example

In this example, we create a pipeline that reads from Apache Pulsar from the specified topic in the config map, and uses the built-in Numamflow [log sink](https://numaflow.numaproj.io/user-guide/sinks/log/) to print all received messages.

#### Pre-requisite

Have a Pulsar cluster running and if you want a partioned topic, you must create it before.

#### Configure the Pulsar consumer

Use the example [ConfigMap](manifests/byte-arr-consumer-config.yaml) to configure the Pulsar source. Make sure that you have the consumer enabled. 

In the ConfigMap:

* `clientConfig` allows you to configure the pulsar client. See all avalaible configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-client):
    * serviceUrl must be specified as it is a required field. This is the brokerServiceUrl.

* `consumerConfig` allows you to set consumer configurations for the pulsar client. Below are some common configurations to consider specifying. See all available configurations [here](https://pulsar.apache.org/reference/#/4.0.x/client/client-configuration-consumer):
    * `topicNames` is the Pulsar topic name to write data to, and is a required field. It must be in the ConfigMap. Currently we only support 1 topic therefore only one **string** value is accepted even though the Pulsar docs indicate that topicNames is of type Set. 
    * If a `subscriptionName` is not specified, the image will give it a default value of sub 
    * `subscriptionInitialPosition` is the initial position of the subscription. It can be `Earliest` or `Latest`. If it is not specified, Pulsar defaults to Latest meaning that the subscription will start consuming messages from the latest available message in the topic. So if messages were produced to a topic before the subscription, they will not be consumered. If you want to start consuming messages from the earliest available message, you can specify `Earliest`.
* `adminConfig` must be specified in order for the consumer to work. The serviceUrl is a required field but note that this is **different** from the serviceUrl file in the clientConfig. This is the webServiceUrl.
* NOTE: To verify if the configMap supports the object Type, look through Pulsar docs to see if that object is an Enum. If it is an Enum, you can provide the value as a String in the config map. Otherwise, check if .yaml files support that type (ex. yaml files support List and Maps). 

#### Create the pipeline

Use the example [pipeline](manifests/byte-arr-consumer-pipeline.yaml) to create the pipeline, using the ConfigMap created in
the previous step. Please make sure that the args list under the consumer vertex matches the file paths in the ConfigMap.

#### Observe the messages
Wait for the pipeline to be up and running. Produce messages to the specified topic and verify that messages are printed in the log sink.