package io.numaproj.pulsar.config.consumer;

import io.numaproj.pulsar.config.EnvLookup;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PulsarConsumerPropertiesTest {

    private static EnvLookup numaflowEnv(String pipeline, String vertex) {
        Map<String, String> m = new HashMap<>();
        if (pipeline != null) {
            m.put("NUMAFLOW_PIPELINE_NAME", pipeline);
        }
        if (vertex != null) {
            m.put("NUMAFLOW_VERTEX_NAME", vertex);
        }
        return m::get;
    }

    @Test
    public void consumerProperties_InitTransformsTopicNamesProperty() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnvLookup(numaflowEnv("myPipeline", "myVertex"));
        Map<String, Object> config = new HashMap<>();
        String topic = "my-test-topic";
        config.put("topicNames", topic);
        config.put("otherKey", "otherValue");
        properties.setConsumerConfig(config);

        properties.init();

        Object topicNamesObj = properties.getConsumerConfig().get("topicNames");
        assertNotNull("topicNames should not be null after init", topicNamesObj);
        assertTrue("topicNames should be of type Set", topicNamesObj instanceof Set);

        Set<?> topicNamesSet = (Set<?>) topicNamesObj;
        assertEquals("The topicNames set should contain one item", 1, topicNamesSet.size());
        assertTrue("The topicNames set should contain the initial topic", topicNamesSet.contains(topic));

        assertEquals("otherKey property should remain unchanged", "otherValue",
                properties.getConsumerConfig().get("otherKey"));
    }

    @Test
    public void consumerProperties_DefaultSubscriptionName() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnvLookup(numaflowEnv("myPipeline", "myVertex"));
        properties.setConsumerConfig(new HashMap<>());

        properties.init();

        Object subscriptionName = properties.getConsumerConfig().get("subscriptionName");
        assertNotNull("subscriptionName should not be null after init", subscriptionName);
        assertEquals(
                "Expected subscription name to be 'myPipeline-myVertex-sub'",
                "myPipeline-myVertex-sub",
                subscriptionName);
    }

    @Test
    public void consumerProperties_useAutoConsumeSchema_defaultTrue() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        assertTrue("useAutoConsumeSchema should default to true", properties.isUseAutoConsumeSchema());
        properties.setUseAutoConsumeSchema(false);
        assertFalse(properties.isUseAutoConsumeSchema());
    }

    @Test
    public void consumerProperties_CustomSubscriptionNameNotOverridden() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnvLookup(numaflowEnv("myPipeline", "myVertex"));
        Map<String, Object> config = new HashMap<>();
        config.put("subscriptionName", "my-custom-sub");
        properties.setConsumerConfig(config);

        properties.init();

        Object subscriptionName = properties.getConsumerConfig().get("subscriptionName");
        assertEquals("my-custom-sub", subscriptionName);
    }
}
