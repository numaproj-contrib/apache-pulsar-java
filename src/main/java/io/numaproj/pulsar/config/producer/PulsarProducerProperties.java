package io.numaproj.pulsar.config.producer;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.producer")
public class PulsarProducerProperties {
    private Map<String, Object> producerConfig = new HashMap<>(); // Default to an empty map

    /**
     * When true (default), the producer uses Schema.AUTO_PRODUCE_BYTES so the broker enforces
     * format compatibility based on registered topic schema.
     */
    private boolean useAutoProduceSchema = true;

    /**
     * When true, messages that fail schema/serialization validation (e.g. SchemaSerializationException)
     * are dropped: the sink responds OK so the message is not retried, and the invalid payload is not
     * sent to Pulsar. When false (default), such messages are reported as failures downstream and may be retried.
     */
    private boolean dropInvalidMessages = false;
}
