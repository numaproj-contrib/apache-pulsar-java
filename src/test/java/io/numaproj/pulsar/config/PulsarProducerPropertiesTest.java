package io.numaproj.pulsar.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

public class PulsarProducerPropertiesTest {

    private PulsarProducerProperties MakePulsarProducerProperties() {
        return new PulsarProducerProperties();
    }

    // Test to check that the producerConfig map is initialized as an empty map by
    // default when a new instance of PulsarClientProperties is created.
    @Test
    public void ProducerConfig_DefaultInitialization_EmptyMap() {
        PulsarProducerProperties properties = MakePulsarProducerProperties();

        Map<String, Object> result = properties.getProducerConfig();

        assertTrue(result.isEmpty());
    }

    // Test to verify that the producerConfig map can be modified through the setter
    // method.
    @Test
    public void ProducerConfig_Setter_ModifiesMap() {
        PulsarProducerProperties properties = MakePulsarProducerProperties();

        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("key", "value");
        properties.setProducerConfig(newConfig);

        Map<String, Object> result = properties.getProducerConfig();
        assertEquals("value", result.get("key"));
    }
}