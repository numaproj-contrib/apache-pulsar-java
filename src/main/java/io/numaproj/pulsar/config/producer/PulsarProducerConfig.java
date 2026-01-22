package io.numaproj.pulsar.config.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;

@Slf4j
@Configuration
public class PulsarProducerConfig {

        @Autowired
        private Environment env;

        @Bean
        @ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true", matchIfMissing = false)
        public Producer<byte[]> pulsarProducer(PulsarClient pulsarClient,
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

                // Optionally load schema for client-side validation if schema file exists
                try {
                        String schemaStr = new String(
                                        new ClassPathResource("schema.avsc").getInputStream().readAllBytes());
                        org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(schemaStr);
                        log.info("Found AVRO schema for client-side validation: {}", avroSchema.toString(true));
                        pulsarProducerProperties.setAvroSchema(Optional.of(avroSchema));
                } catch (Exception e) {
                        log.info("No schema.avsc found or error loading schema. Client-side validation will be disabled.");
                        pulsarProducerProperties.setAvroSchema(Optional.empty());
                }

                // Create producer with byte[] schema for maximum flexibility
                return pulsarClient.newProducer(Schema.BYTES)
                                .loadConf(producerConfig)
                                .create();
        }
}