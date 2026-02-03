package io.numaproj.pulsar.config.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Configuration
public class PulsarProducerConfig {

    @Autowired
    private Environment env;

    @Bean
    @ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Producer<byte[]> pulsarProducer(PulsarClient pulsarClient, PulsarProducerProperties pulsarProducerProperties, PulsarAdmin pulsarAdmin)
            throws Exception {
        String podName = env.getProperty("NUMAFLOW_POD", "pod-" + UUID.randomUUID());
        String producerName = "producerName";

        Map<String, Object> producerConfig = pulsarProducerProperties.getProducerConfig();
        if (producerConfig.containsKey(producerName)) {
            log.warn("User configured a 'producerName' in the config, but this can cause errors if multiple pods spin "
                    + "up with the same name. Overriding with '{}'", podName);
        }
        producerConfig.put(producerName, podName);

        // Validate that the topic configured in the producer config 
        String topicName = (String) producerConfig.get("topicName");
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic name must be configured in producer config");
        }

        validateTopicExists(pulsarAdmin, topicName);

        return pulsarClient.newProducer(Schema.BYTES)
                .loadConf(producerConfig)
                .create();
    }

    private void validateTopicExists(PulsarAdmin pulsarAdmin, String topicName) {
        try {
            // Extract namespace from topic name: persistent://tenant/namespace/topic
            String[] parts = topicName.split("/");
            if (parts.length < 4) {
                throw new IllegalArgumentException("Invalid topic name format: " + topicName);
            }
            String namespace = parts[2] + "/" + parts[3];
            
            // List all topics in the namespace - works for both partitioned and non-partitioned
            java.util.List<String> topics = pulsarAdmin.topics().getList(namespace);
            
            // Check if topic exists (exact match for non-partitioned, or partition-0 for partitioned)
            boolean topicExists = topics.contains(topicName) || 
                                  topics.stream().anyMatch(t -> t.startsWith(topicName + "-partition-"));
            
            if (topicExists) {
                log.info("Topic '{}' exists", topicName);
                return;
            }
            
            String errorMsg = String.format("Topic '%s' does not exist. Please create the topic before starting the producer.", topicName);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (PulsarAdminException e) {
            log.error("Failed to verify topic existence for '{}'", topicName, e);
            throw new RuntimeException("Failed to verify topic existence", e);
        }
    }
}