package io.numaproj.pulsar.config.producer;

import io.numaproj.pulsar.config.EnvLookup;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.Topics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class PulsarProducerConfigTest {

    private PulsarProducerProperties producerProperties;
    private PulsarClient mockClient;
    @SuppressWarnings("unchecked")
    private ProducerBuilder<byte[]> mockProducerBuilder;
    private Producer<byte[]> mockProducer;
    private PulsarAdmin mockAdmin;
    private Topics mockTopics;

    private static EnvLookup podEnv(String pod) {
        Map<String, String> m = new HashMap<>();
        if (pod != null) {
            m.put("NUMAFLOW_POD", pod);
        }
        return m::get;
    }

    @Before
    public void setUp() throws Exception {
        producerProperties = new PulsarProducerProperties();
        // Schema.BYTES: AUTO_PRODUCE_BYTES is not initialized until a real broker connection; getSchemaInfo() after mock create() would fail.
        producerProperties.setUseAutoProduceSchema(false);

        mockClient = mock(PulsarClient.class);
        mockAdmin = mock(PulsarAdmin.class);
        mockTopics = mock(Topics.class);

        @SuppressWarnings("unchecked")
        ProducerBuilder<byte[]> builder = mock(ProducerBuilder.class);
        mockProducerBuilder = builder;

        mockProducer = mock(Producer.class);

        when(mockClient.newProducer(any(Schema.class))).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);

        when(mockAdmin.topics()).thenReturn(mockTopics);
        when(mockTopics.getList(anyString())).thenReturn(java.util.List.of("persistent://tenant/namespace/test-topic"));
    }

    @After
    public void tearDown() {
        producerProperties = null;
        mockClient = null;
        mockProducerBuilder = null;
        mockProducer = null;
        mockAdmin = null;
        mockTopics = null;
    }

    @Test
    public void pulsarProducer_validConfig() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        producerProperties.setProducerConfig(producerConfig);

        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.List.of("persistent://tenant/namespace/test-topic"));

        Producer<byte[]> producer = PulsarProducerConfig.create(
                mockClient, producerProperties, mockAdmin, podEnv("pod-from-env"));

        assertNotNull("Producer should be created", producer);

        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder).loadConf(argThat(map -> "persistent://tenant/namespace/test-topic".equals(map.get("topicName"))));
        verify(mockProducerBuilder).create();
    }

    @Test
    public void pulsarProducer_missingTopicName_throwsException() throws Exception {
        producerProperties.setProducerConfig(new HashMap<>());

        try {
            PulsarProducerConfig.create(mockClient, producerProperties, mockAdmin, podEnv("p"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test
    public void pulsarProducer_invalidTopicFormat_throwsException() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "invalid-topic");
        producerProperties.setProducerConfig(producerConfig);

        try {
            PulsarProducerConfig.create(mockClient, producerProperties, mockAdmin, podEnv("p"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("Invalid topic name format"));
        }
    }

    @Test
    public void pulsarProducer_ProducerNameFromEnvVarNoUserConfig() throws Exception {
        final String envPodName = "NUMAFLOW_POD_VALUE";
        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        producerProperties.setProducerConfig(emptyConfig);

        Producer<byte[]> producer = PulsarProducerConfig.create(
                mockClient, producerProperties, mockAdmin, podEnv(envPodName));

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    @Test
    public void pulsarProducer_ProducerNameOverridden() throws Exception {
        final String envPodName = "my-env-pod";
        Map<String, Object> userConfig = new HashMap<>();
        userConfig.put("producerName", "userProvidedName");
        userConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        producerProperties.setProducerConfig(userConfig);

        Producer<byte[]> producer = PulsarProducerConfig.create(
                mockClient, producerProperties, mockAdmin, podEnv(envPodName));

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    @Test
    public void pulsarProducer_NoEnvVariableFoundFallbackName() throws Exception {
        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        producerProperties.setProducerConfig(emptyConfig);

        Producer<byte[]> producer = PulsarProducerConfig.create(
                mockClient, producerProperties, mockAdmin, podEnv(null));

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(captor.capture());

        String producerName = (String) captor.getValue().get("producerName");
        assertNotNull("Producer name should not be null", producerName);
        assertTrue("Producer name should start with 'pod-'", producerName.startsWith("pod-"));
    }

    @Test
    public void pulsarProducer_topicDoesNotExist_throwsException() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/non-existent-topic");
        producerProperties.setProducerConfig(producerConfig);

        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.Collections.emptyList());

        try {
            PulsarProducerConfig.create(mockClient, producerProperties, mockAdmin, podEnv("p"));
            fail("expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertTrue("Error message should contain topic name",
                    exception.getMessage().contains("non-existent-topic"));
            assertTrue("Error message should indicate topic doesn't exist",
                    exception.getMessage().contains("does not exist"));
        }

        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder, never()).create();
    }

    @Test
    public void pulsarProducer_useAutoProduceSchemaFalse_dropInvalidMessagesTrue_throwsException() throws Exception {
        PulsarProducerProperties properties = new PulsarProducerProperties();
        properties.setUseAutoProduceSchema(false);
        properties.setDropInvalidMessages(true);
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        properties.setProducerConfig(producerConfig);

        try {
            PulsarProducerConfig.create(mockClient, properties, mockAdmin, podEnv("p"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertTrue("Message should describe invalid combination",
                    exception.getMessage().contains("useAutoProduceSchema=false and dropInvalidMessages=true"));
            assertTrue("Message should mention dropInvalidMessages only applies with auto-produce",
                    exception.getMessage().contains("dropInvalidMessages only applies when useAutoProduceSchema is true"));
        }
        verify(mockProducerBuilder, never()).create();
    }

    @Test
    public void pulsarProducer_partitionedTopicExists() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/partitioned-topic");
        producerProperties.setProducerConfig(producerConfig);

        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.List.of("persistent://tenant/namespace/partitioned-topic-partition-0"));

        Producer<byte[]> producer = PulsarProducerConfig.create(
                mockClient, producerProperties, mockAdmin, podEnv("p"));

        assertNotNull("Producer should be created", producer);
        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder).create();
    }
}
