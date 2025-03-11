package io.numaproj.pulsar.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
    private Map<String, Object> consumerConfig = new HashMap<>(); // Default to an empty map

    @PostConstruct
    public void init() {
        // Pulsar expects topicNames to be type set, but the configMap accepts a string
        if (consumerConfig.containsKey("topicNames")) {
            String topicName = (String) consumerConfig.remove("topicNames");
            Set<String> topicNames = new HashSet<>();
            topicNames.add(topicName);
            consumerConfig.put("topicNames", topicNames);
        }
        log.info("Consumer Config: " + consumerConfig);
    }
}
