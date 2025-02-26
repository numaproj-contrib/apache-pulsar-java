package io.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;

import java.util.Map;
import java.util.UUID;

import org.apache.pulsar.client.api.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import io.numaproj.pulsar.config.PulsarClientProperties;
import io.numaproj.pulsar.config.PulsarProducerProperties;
import lombok.extern.slf4j.Slf4j;

/***
 *
 * For each @Configuration annotated class it finds, Spring will create an
 * instance of that configuration class
 * and then call the methods marked with @Bean to get the instances of beans.
 * The objects returned by these methods are then registered within the Spring
 * application context.
 */
@Slf4j
@Configuration
public class PulsarConfig {
    @Autowired
    private Environment env;

    @Bean
    public PulsarClient pulsarClient(PulsarClientProperties pulsarClientProperties) throws PulsarClientException {
        return PulsarClient.builder()
                .loadConf(pulsarClientProperties.getClientConfig())
                .build();
    }

    @Bean
    public Producer<byte[]> pulsarProducer(PulsarClient pulsarClient, PulsarProducerProperties pulsarProducerProperties)
            throws Exception {
        String podName = env.getProperty("NUMAFLOW_POD", "pod-" + UUID.randomUUID());

        if (podName == null || podName.isBlank()) {
            podName = "pod-" + UUID.randomUUID();
            log.warn("NUMAFLOW_POD environment variable not found. Generating fallback Producer name: {}", podName);
        }

        // Always override the user-specified producerName with the pod name
        Map<String, Object> producerConfig = pulsarProducerProperties.getProducerConfig();
        if (producerConfig.containsKey("producerName")) {
            log.warn("User configured a 'producerName' in the config, but this can cause errors if multiple pods spin "
                    + "up with the same name. Overriding with '{}'", podName);
        }
        producerConfig.put("producerName", podName);
        log.info("The podname is {}", podName);

        return pulsarClient.newProducer(Schema.BYTES)
                .loadConf(producerConfig)
                .create();
    }
}
