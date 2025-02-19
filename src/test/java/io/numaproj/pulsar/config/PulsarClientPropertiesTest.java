package io.numaproj.pulsar.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

public class PulsarClientPropertiesTest {

    private PulsarClientProperties MakePulsarClientProperties() {
        return new PulsarClientProperties();
    }

    // Test to check that the clientConfig map is initialized as an empty map by default when a new instance of PulsarClientProperties is created.
    @Test
    public void ClientConfig_DefaultInitialization_EmptyMap() {
        PulsarClientProperties properties = MakePulsarClientProperties();
        
        Map<String, Object> result = properties.getClientConfig();
        
        assertTrue(result.isEmpty());
    }

    // Test to verify that the clientConfig map can be modified through the setter method.
    @Test  
    public void ClientConfig_Setter_ModifiesMap() {
        PulsarClientProperties properties = MakePulsarClientProperties();
        
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("key", "value");
        properties.setClientConfig(newConfig);
        
        Map<String, Object> clientConfig = properties.getClientConfig();
        assertEquals("value", clientConfig.get("key"));
    }
}