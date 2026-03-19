package io.numaproj.pulsar.config.consumer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
public class PulsarConsumerProperties {

    private boolean enabled = false;

    private Map<String, Object> consumerConfig = new HashMap<>(); // Default to an empty map

    /**
     * When true (default), the consumer uses Schema.AUTO_CONSUME so the client validates
     * each message against the topic schema when decoding. When false, uses Schema.BYTES
     * (no schema check; messages are read as raw bytes).
     * When the topic has no schema (Pulsar treats it as BYTES), no validation and no decoding
     * is performed on the message bytes; they are passed through as bytes.
     */
    private boolean useAutoConsumeSchema = true;

    public void init() {
        // Pulsar expects topicNames to be type Set<String>. Config accepts a single string
        // (one topic) or comma-separated topics (e.g. "topic1,topic2,topic3").
        String topicNameKey = "topicNames";
        if (consumerConfig.containsKey(topicNameKey)) {
            Object topicNameObj = consumerConfig.get(topicNameKey);
            if (!(topicNameObj instanceof String)) {
                throw new IllegalArgumentException(
                        String.format("Value for key '%s' must be a String, but found: %s",
                                topicNameKey,
                                topicNameObj == null ? "null" : topicNameObj.getClass().getName()));
            }
            String topicNamesStr = (String) consumerConfig.remove(topicNameKey);
            Set<String> topicNames = Arrays.stream(topicNamesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (topicNames.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Value for key '%s' must contain at least one non-empty topic name", topicNameKey));
            }
            consumerConfig.put(topicNameKey, topicNames);
        }

        // Get the pipeline and vertex names from the environment variables to use as a default for the subscription name
        String pipelineName = System.getenv("NUMAFLOW_PIPELINE_NAME");
        String vertexName = System.getenv("NUMAFLOW_VERTEX_NAME");
        String defaultSubscriptionName = String.join("-", pipelineName != null ? pipelineName : "pipeline",
                vertexName != null ? vertexName : "vertex", "sub");

        // If 'subscriptionName' not present, provide a default
        String subscriptionNameKey = "subscriptionName";
        if (!consumerConfig.containsKey(subscriptionNameKey)) {
            consumerConfig.put(subscriptionNameKey, defaultSubscriptionName);
            log.info("No subscriptionName provided. Setting default: '{}'", defaultSubscriptionName);
        } else {
            log.info("subscriptionName was already set, leaving as-is.");
        }
    }
}
