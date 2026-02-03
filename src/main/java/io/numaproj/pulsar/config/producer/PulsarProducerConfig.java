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

        String topicName = (String) producerConfig.get("topicName");
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic name must be configured in producer config");
        }

        try {
            // Check if topic exists by getting stats - works for both partitioned and non-partitioned topics
            pulsarAdmin.topics().getStats(topicName, true);
            log.info("Topic '{}' exists, proceeding with producer creation", topicName);
        } catch (PulsarAdminException.NotFoundException e) {
            String errorMsg = String.format("Topic '%s' does not exist. Please create the topic before starting the producer.", topicName);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg, e);
        } catch (PulsarAdminException e) {
            log.error("Failed to verify topic existence for '{}'", topicName, e);
            throw new RuntimeException("Failed to verify topic existence", e);
        }

        return pulsarClient.newProducer(Schema.BYTES)
                .loadConf(producerConfig)
                .create();
    }
}