package io.numaproj.pulsar.common;

/**
 * Constants for Pulsar message headers and metadata.
 */
public class Constants {
    
    public static final String PULSAR_PRODUCER_NAME = "x-pulsar-producer-name";
    public static final String PULSAR_MESSAGE_ID = "x-pulsar-message-id";
    public static final String PULSAR_TOPIC_NAME = "x-pulsar-topic-name";
    public static final String PULSAR_PUBLISH_TIME = "x-pulsar-publish-time";
    public static final String PULSAR_EVENT_TIME = "x-pulsar-event-time";
    public static final String PULSAR_REDELIVERY_COUNT = "x-pulsar-redelivery-count";
    
    private Constants() {
        // Utility class, prevent instantiation
    }
}
