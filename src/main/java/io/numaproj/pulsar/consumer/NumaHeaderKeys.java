package io.numaproj.pulsar.consumer;
/**
 * Header keys for Pulsar message metadata passed to Numaflow.
 */
class NumaHeaderKeys {

    static final String PULSAR_PRODUCER_NAME = "x-pulsar-producer-name";
    static final String PULSAR_MESSAGE_ID = "x-pulsar-message-id";
    static final String PULSAR_TOPIC_NAME = "x-pulsar-topic-name";
    static final String PULSAR_PUBLISH_TIME = "x-pulsar-publish-time";
    static final String PULSAR_EVENT_TIME = "x-pulsar-event-time";
    static final String PULSAR_REDELIVERY_COUNT = "x-pulsar-redelivery-count";

    private NumaHeaderKeys() {
        // Utility class, prevent instantiation
    }
}
