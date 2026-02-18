package io.numaproj.pulsar.config.consumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PulsarConsumerPropertiesTest {

    private Environment mockEnv;

    @Before
    public void setUp() {
        mockEnv = Mockito.mock(Environment.class);
        when(mockEnv.getProperty("NUMAFLOW_PIPELINE_NAME")).thenReturn("myPipeline");
        when(mockEnv.getProperty("NUMAFLOW_VERTEX_NAME")).thenReturn("myVertex");
    }

    @Test
    public void consumerProperties_InitTransformsTopicNamesProperty() {
        // Prepare a properties instance with a string value for topicNames
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnv(mockEnv);
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

    /**
     * Verify that if 'subscriptionName' is not specified in the consumerConfig,
     * it becomes the default value.
     */
    @Test
    public void consumerProperties_DefaultSubscriptionName() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnv(mockEnv);
        Map<String, Object> config = new HashMap<>();
        // No subscription name provided
        properties.setConsumerConfig(config);

        properties.init();

        Object subscriptionName = properties.getConsumerConfig().get("subscriptionName");
        assertNotNull("subscriptionName should not be null after init", subscriptionName);
        // Verify the expected default format: myPipeline-myVertex-sub
        assertEquals(
            "Expected subscription name to be 'myPipeline-myVertex-sub'",
            "myPipeline-myVertex-sub",
            subscriptionName
        );
    }

    @Test
    public void consumerProperties_useAutoConsumeSchema_defaultTrue() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        assertTrue("useAutoConsumeSchema should default to true", properties.isUseAutoConsumeSchema());
        properties.setUseAutoConsumeSchema(false);
        assertFalse(properties.isUseAutoConsumeSchema());
    }

    @Test
    public void consumerProperties_dropMessageOnSchemaValidationFailure_defaultFalse() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        assertFalse("dropMessageOnSchemaValidationFailure should default to false", properties.isDropMessageOnSchemaValidationFailure());
        properties.setDropMessageOnSchemaValidationFailure(true);
        assertTrue(properties.isDropMessageOnSchemaValidationFailure());
    }

    /**
     * Verify that if 'subscriptionName' is specified in the consumerConfig,
     * it is not overwritten by the default value.
     */
    @Test
    public void consumerProperties_CustomSubscriptionNameNotOverridden() {
        PulsarConsumerProperties properties = new PulsarConsumerProperties();
        properties.setEnv(mockEnv);
        Map<String, Object> config = new HashMap<>();
        config.put("subscriptionName", "my-custom-sub");
        properties.setConsumerConfig(config);

        properties.init();

        // subscriptionName should remain the same
        Object subscriptionName = properties.getConsumerConfig().get("subscriptionName");
        assertEquals("my-custom-sub", subscriptionName);
    }
}
