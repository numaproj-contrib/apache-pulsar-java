package io.numaproj.pulsar.config.consumer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.consumer")
@Slf4j
public class PulsarConsumerProperties {
    @Autowired
    private Environment env;

    private Map<String, Object> consumerConfig = new HashMap<>(); // Default to an empty map

    /**
     * When true (default), the consumer uses Schema.AUTO_CONSUME so the client validates
     * each message against the topic schema when decoding. When false, uses Schema.BYTES
     * (no schema check; messages are read as raw bytes). For the byte-array consumer path,
     * dropMessageOnSchemaValidationFailure is irrelevant and not used.
     */
    private boolean useAutoConsumeSchema = true;

    /**
     * When true, a message that fails schema validation (e.g. wrong Avro schema) is dropped:
     * the message is ack'd as if processed and the consumer continues. When false (default),
     * validation failure throws. Only applies when useAutoConsumeSchema is true; when false
     * (byte-array consumer) this flag is ignored and must not be true.
     */
    private boolean dropMessageOnSchemaValidationFailure = false;

    @PostConstruct
    public void init() {
        if (!useAutoConsumeSchema && dropMessageOnSchemaValidationFailure) {
            log.warn("dropMessageOnSchemaValidationFailure is ignored when useAutoConsumeSchema is false (byte-array consumer); forcing to false.");
            dropMessageOnSchemaValidationFailure = false;
        }
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

        final String defaultSubscriptionName = String.join("-", env.getProperty("NUMAFLOW_PIPELINE_NAME"),
                env.getProperty("NUMAFLOW_VERTEX_NAME"), "sub");

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
