package io.numaproj.pulsar.config.consumer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.consumer")
@Slf4j
public class PulsarConsumerProperties {
    @Autowired
    private Environment env;

    private Map<String, Object> consumerConfig = new HashMap<>(); // Default to an empty map

    @PostConstruct
    public void init() {
        // Pulsar expects topicNames to be type set, but the configMap accepts a string
        String topicNameKey = "topicNames";
        if (consumerConfig.containsKey(topicNameKey)) {
            Object topicNameObj = consumerConfig.get(topicNameKey);
            if (!(topicNameObj instanceof String)) {
                throw new IllegalArgumentException(
                        String.format("Value for key '%s' must be a String, but found: %s",
                                topicNameKey,
                                topicNameObj == null ? "null" : topicNameObj.getClass().getName()));
            }
            String topicName = (String) consumerConfig.remove(topicNameKey);
            Set<String> topicNames = new HashSet<>();
            topicNames.add(topicName);
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
