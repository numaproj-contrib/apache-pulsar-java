package io.numaproj.pulsar.config.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.UUID;

@Configuration
public class PulsarProducerConfig {

    @Autowired
    private Environment env;

    @Bean
    @ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Producer<byte[]> pulsarProducer(PulsarClient pulsarClient, PulsarProducerProperties pulsarProducerProperties)
            throws Exception {
        String podName = env.getProperty("NUMAFLOW_POD", "pod-" + UUID.randomUUID());
        String producerName = "producerName";

        Map<String, Object> producerConfig = pulsarProducerProperties.getProducerConfig();
        if (producerConfig.containsKey(producerName)) {
            // Log.warning would go here
        }
        producerConfig.put(producerName, podName);

        return pulsarClient.newProducer(Schema.BYTES)
                .loadConf(producerConfig)
                .create();
    }
}