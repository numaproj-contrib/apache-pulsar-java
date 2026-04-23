package io.numaproj.pulsar.config.producer;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed representation of the pulsar.producer section of application.yml.
 * Call validateConfig() before constructing a producer.
 */
@Getter
@Setter
public class PulsarProducerProperties {

    /** True to run as a Numaflow sink (producer). */
    private boolean enabled = false;

    /** Raw Pulsar producer configuration. The producerName field is overridden with the pod name at runtime. */
    private Map<String, Object> producerConfig = new HashMap<>();

    /**
     * When true (default), the producer uses Schema.AUTO_PRODUCE_BYTES so the broker enforces
     * format compatibility based on registered topic schema.
     */
    private boolean useAutoProduceSchema = true;

    /**
     * /**
     * When true, messages that fail schema/serialization validation (e.g. SchemaSerializationException) are dropped: 
     * the sink responds OK so the message is not retried, and the invalid payload is not sent to Pulsar. 
     * When false (default), such messages are reported as failures downstream and may be retried.
     * Value is only meaningful when useAutoProduceSchema is true.
     */
    private boolean dropInvalidMessages = false;

    /**
     * Validates the configuration to catch unsupported combinations of fields.
     *
     * @throws IllegalArgumentException if useAutoProduceSchema is false and dropInvalidMessages is true
     */
    public void validateConfig() {
        if (!useAutoProduceSchema && dropInvalidMessages) {
            throw new IllegalArgumentException(
                    "Invalid combination: useAutoProduceSchema=false and dropInvalidMessages=true. "
                            + "dropInvalidMessages only applies when useAutoProduceSchema is true (broker validates schema). "
                            + "With Schema.BYTES there is no schema validation, so dropInvalidMessages has no effect.");
        }
    }
}
