package io.numaproj.pulsar.config.producer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.apache.avro.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.producer")
public class PulsarProducerProperties {
    private Map<String, Object> producerConfig = new HashMap<>(); // Default to an empty map
    private Optional<Schema> avroSchema = Optional.empty(); // Optional schema for client-side validation
}
