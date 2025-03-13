package io.numaproj.pulsar.config;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PulsarConsumerPropertiesTest {

    @Test
    public void testInitTransformsTopicNamesProperty() {
        // Prepare a properties instance with a string value for topicNames
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        Map<String, Object> config = new HashMap<>();
        String topic = "my-test-topic";
        config.put("topicNames", topic);
        // Also add another unrelated property
        config.put("otherKey", "otherValue");
        properties.setConsumerConfig(config);

        // When init is called, it should convert the topicNames value to a Set
        properties.init();

        // Verify that topicNames is now a Set containing the original string
        Object topicNamesObj = properties.getConsumerConfig().get("topicNames");
        assertNotNull("topicNames should not be null after init", topicNamesObj);
        assertTrue("topicNames should be of type Set", topicNamesObj instanceof Set);

        Set<?> topicNamesSet = (Set<?>) topicNamesObj;
        assertEquals("The topicNames set should contain one item", 1, topicNamesSet.size());
        assertTrue("The topicNames set should contain the initial topic", topicNamesSet.contains(topic));

        // Ensure that unrelated properties remain unchanged
        assertEquals("otherKey property should remain unchanged", "otherValue",
                properties.getConsumerConfig().get("otherKey"));
    }
}