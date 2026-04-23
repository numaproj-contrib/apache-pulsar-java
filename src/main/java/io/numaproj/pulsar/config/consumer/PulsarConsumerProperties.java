package io.numaproj.pulsar.config.consumer;

import io.numaproj.pulsar.config.EnvLookup;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parsed representation of the pulsar.consumer section of application.yml.
 * Call init() once before use to normalize topicNames and set a default subscriptionName.
 */
@Slf4j
@Getter
@Setter
public class PulsarConsumerProperties {

    /** True to run as a Numaflow source (consumer). */
    private boolean enabled = false;

    /** Raw Pulsar consumer configuration keyed by Pulsar consumer config names. */
    private Map<String, Object> consumerConfig = new HashMap<>();

    /** Resolver for NUMAFLOW_PIPELINE_NAME and NUMAFLOW_VERTEX_NAME environment variables. Defaults to OS env; may be replaced in tests.*/
    private EnvLookup envLookup = EnvLookup.system();

    /**
     * When true (default), the consumer uses Schema.AUTO_CONSUME so the client validates
     * each message against the topic schema when decoding. When false, uses Schema.BYTES
     * (no schema check; messages are read as raw bytes).
     * When the topic has no schema (Pulsar treats it as BYTES), no validation and no decoding
     * is performed on the message bytes; they are passed through as bytes.
     */
    private boolean useAutoConsumeSchema = true;

    /**
     * Normalizes topicNames into a Set<String> and fills in a default subscriptionName
     * based on the NUMAFLOW_PIPELINE_NAME and NUMAFLOW_VERTEX_NAME environment variables.
     *
     * @throws IllegalArgumentException if topicNames is not a String or resolves to zero topics
     */
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
        String pipelineName = envLookup.get("NUMAFLOW_PIPELINE_NAME");
        String vertexName = envLookup.get("NUMAFLOW_VERTEX_NAME");
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
