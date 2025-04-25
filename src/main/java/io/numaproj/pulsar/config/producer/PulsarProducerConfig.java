package io.numaproj.pulsar.config.producer;

import io.numaproj.pulsar.model.numagen;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.apache.avro.Schema.Parser;
import io.numaproj.pulsar.model.numagen;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Configuration
public class PulsarProducerConfig {

        @Autowired
        private Environment env;

        @Bean
        @ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true", matchIfMissing = false)
        public Producer<numagen> pulsarProducer(PulsarClient pulsarClient,
                        PulsarProducerProperties pulsarProducerProperties)
                        throws Exception {
                String podName = env.getProperty("NUMAFLOW_POD", "pod-" + UUID.randomUUID());
                String producerName = "producerName";

                Map<String, Object> producerConfig = pulsarProducerProperties.getProducerConfig();
                if (producerConfig.containsKey(producerName)) {
                        log.warn("User configured a 'producerName' in the config, but this can cause errors if multiple pods spin "
                                        + "up with the same name. Overriding with '{}'", podName);
                }
                producerConfig.put(producerName, podName);

                

                // Use the schema from the Avro-generated class
                return pulsarClient.newProducer(Schema.AVRO(numagen.class))
                                .loadConf(producerConfig)
                                .create();
        }
}