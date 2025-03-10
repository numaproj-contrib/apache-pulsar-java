package io.numaproj.pulsar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.consumer")
public class PulsarConsumerProperties {
    private Map<String, Object> consumerConfig = new HashMap<>(); // Default to an empty map
}
