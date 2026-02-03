
package io.numaproj.pulsar.config.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Topics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import io.numaproj.pulsar.config.client.PulsarClientConfig;
import io.numaproj.pulsar.config.client.PulsarClientProperties;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = PulsarProducerConfig.class)
public class PulsarProducerConfigTest {

    private PulsarProducerConfig pulsarProducerConfig;
    private Environment mockEnvironment;

    // Objects used only by specific test groups
    private PulsarProducerConfig spiedConfig;
    private PulsarClient mockClient;
    private PulsarProducerProperties mockProducerProperties;
    private ProducerBuilder<byte[]> mockProducerBuilder;
    private Producer<byte[]> mockProducer;
    private PulsarAdmin mockAdmin;
    private Topics mockTopics;

    @Before
    public void setUp() throws Exception {
        pulsarProducerConfig = new PulsarProducerConfig();
        mockEnvironment = mock(Environment.class);
        ReflectionTestUtils.setField(pulsarProducerConfig, "env", mockEnvironment);

        mockProducerProperties = mock(PulsarProducerProperties.class);
        mockClient = mock(PulsarClient.class);
        mockAdmin = mock(PulsarAdmin.class);
        mockTopics = mock(Topics.class);

        spiedConfig = spy(pulsarProducerConfig);
        PulsarClientConfig mockClientConfig = mock(PulsarClientConfig.class);
        doReturn(mockClient).when(mockClientConfig).pulsarClient(any(PulsarClientProperties.class));

        @SuppressWarnings("unchecked")
        ProducerBuilder<byte[]> builder = mock(ProducerBuilder.class);
        mockProducerBuilder = builder;

        mockProducer = mock(Producer.class);

        when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
        
        when(mockAdmin.topics()).thenReturn(mockTopics);
        // By default, return a list with a test topic for validation (in tenant/namespace format)
        when(mockTopics.getList(anyString())).thenReturn(java.util.List.of("persistent://tenant/namespace/test-topic"));
    }

    @After
    public void tearDown() {
        pulsarProducerConfig = null;
        spiedConfig = null;
        mockProducerProperties = null;
        mockClient = null;
        mockProducerBuilder = null;
        mockProducer = null;
        mockEnvironment = null;
        mockAdmin = null;
        mockTopics = null;
    }
    // Test to successfully create Producer bean with valid configuration properties
    @Test
    public void pulsarProducer_validConfig() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);
        
        // Simulate topic exists in the list
        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.List.of("persistent://tenant/namespace/test-topic"));

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin);

        assertNotNull("Producer should be created", producer);

        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder).loadConf(argThat(map -> "persistent://tenant/namespace/test-topic".equals(map.get("topicName"))));
        verify(mockProducerBuilder).create();
        verify(mockProducerProperties).getProducerConfig();
    }

    // Test which ensures an error is thrown if pulsar producer isn't created with
    // topicName
    @Test
    public void pulsarProducer_missingTopicName_throwsException() throws Exception {
        when(mockProducerProperties.getProducerConfig()).thenReturn(new HashMap<>());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pulsarProducerConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin));

        assertTrue(exception.getMessage().contains("Topic name must be configured in producer config"));
    }
    
    // Test for invalid topic name format
    @Test
    public void pulsarProducer_invalidTopicFormat_throwsException() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "invalid-topic");  // Missing persistent:// prefix
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin));

        assertTrue(exception.getMessage().contains("Invalid topic name format"));
    }

    // Test for environment variable is set, and user does NOT specify producerName
    @Test
    public void pulsarProducer_ProducerNameFromEnvVarNoUserConfig() throws Exception {
        final String envPodName = "NUMAFLOW_POD_VALUE";
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin);

        assertNotNull(producer);
        // Check that the "producerName" is set to envPodName
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    // Test for environment variable is set, but user explicitly sets producerName:
    @Test
    public void pulsarProducer_ProducerNameOverridden() throws Exception {
        final String envPodName = "my-env-pod";
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

        Map<String, Object> userConfig = new HashMap<>();
        userConfig.put("producerName", "userProvidedName");
        userConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(userConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin);

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    // Test for if NUMAFLOW_POD environment variable is not set
    @Test
    public void pulsarProducer_NoEnvVariableFoundFallbackName() throws Exception {
        // Simulate NUMAFLOW_POD not being set by returning null
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin);

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(captor.capture());
        
        String producerName = (String) captor.getValue().get("producerName");
        assertNotNull("Producer name should not be null", producerName);
        assertTrue("Producer name should start with 'pod-'", producerName.startsWith("pod-"));
    }

    // Test for topic that does not exist - should throw IllegalStateException
    @Test
    public void pulsarProducer_topicDoesNotExist_throwsException() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/non-existent-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

        // Return empty list (no topics exist in namespace)
        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.Collections.emptyList());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin));

        assertTrue("Error message should contain topic name", 
                exception.getMessage().contains("non-existent-topic"));
        assertTrue("Error message should indicate topic doesn't exist", 
                exception.getMessage().contains("does not exist"));
        
        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder, never()).create();
    }

    // Test for other PulsarAdminException during topic validation
    @Test
    public void pulsarProducer_topicValidationFails_throwsRuntimeException() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

        PulsarAdminException adminException = new PulsarAdminException(new Exception("Connection error"));
        when(mockTopics.getList(eq("tenant/namespace")))
                .thenThrow(adminException);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin));

        assertTrue("Error message should indicate verification failure", 
                exception.getMessage().contains("Failed to verify topic existence"));
        
        verify(mockTopics).getList("tenant/namespace");
        // Verify that producer was never created
        verify(mockProducerBuilder, never()).create();
    }
    
    // Test for partitioned topic that exists (happy path)
    @Test
    public void pulsarProducer_partitionedTopicExists() throws Exception {
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "persistent://tenant/namespace/partitioned-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

        // Simulate partitioned topic by returning partition-0 in the list
        when(mockTopics.getList(eq("tenant/namespace")))
                .thenReturn(java.util.List.of("persistent://tenant/namespace/partitioned-topic-partition-0"));

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties, mockAdmin);

        assertNotNull("Producer should be created", producer);
        verify(mockTopics).getList("tenant/namespace");
        verify(mockProducerBuilder).create();
    }
}