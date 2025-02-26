package io.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import io.numaproj.pulsar.config.PulsarClientProperties;
import io.numaproj.pulsar.config.PulsarProducerProperties;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = PulsarConfig.class)
public class PulsarConfigTest {

    // Common objects used by most tests
    private PulsarConfig pulsarConfig;
    private Environment mockEnvironment;

    // Objects used only by specific test groups
    private PulsarConfig spiedConfig;
    private PulsarClient mockClient;
    private PulsarClientProperties mockClientProperties;
    private PulsarProducerProperties mockProducerProperties;
    private ProducerBuilder<byte[]> mockProducerBuilder;
    private Producer<byte[]> mockProducer;

    @Before
    public void setUp() {
        // Initialize only the base objects needed by all tests
        pulsarConfig = new PulsarConfig();
        mockEnvironment = mock(Environment.class);
        ReflectionTestUtils.setField(pulsarConfig, "env", mockEnvironment);
        
        // Initialize client properties used by client tests
        mockClientProperties = mock(PulsarClientProperties.class);
    }

    @After
    public void tearDown() {
        // Cleanup references
        pulsarConfig = null;
        spiedConfig = null;
        mockClientProperties = null;
        mockProducerProperties = null;
        mockClient = null;
        mockProducerBuilder = null;
        mockProducer = null;
        mockEnvironment = null;
    }

    // Helper method to set up producer-specific test dependencies
    private void setUpProducerTest() throws Exception {
        // Only initialize these when needed for producer tests
        mockProducerProperties = mock(PulsarProducerProperties.class);
        mockClient = mock(PulsarClient.class);
        
        spiedConfig = spy(pulsarConfig);
        doReturn(mockClient).when(spiedConfig).pulsarClient(any(PulsarClientProperties.class));

        // ProducerBuilder
        @SuppressWarnings("unchecked")
        ProducerBuilder<byte[]> builder = mock(ProducerBuilder.class);
        mockProducerBuilder = builder;

        // Producer
        mockProducer = mock(Producer.class);

        when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
    }

    // Test to create PulsarClient bean with valid configuration properties
    @Test
    public void pulsarClient_validConfig() throws Exception {
        Map<String, Object> config = new HashMap<>();
        // URL must include the protocol (pulsar:// or pulsar+ssl://)
        config.put("serviceUrl", "pulsar://test:1234");
        when(mockClientProperties.getClientConfig()).thenReturn(config);

        PulsarClient client = pulsarConfig.pulsarClient(mockClientProperties);

        assertNotNull(client);
        verify(mockClientProperties).getClientConfig();
    }

    // Test to successfully create Producer bean with valid configuration properties
    @Test
    public void pulsarProducer_validConfig() throws Exception {
        setUpProducerTest();
        
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

        assertNotNull("Producer should be created", producer);

        verify(mockProducerBuilder).loadConf(argThat(map -> 
            "test-topic".equals(map.get("topicName"))));
        verify(mockProducerBuilder).create();
        verify(mockProducerProperties).getProducerConfig();
    }

    // Test to ensure an error is thrown if pulsar client isn't created with service url
    @Test
    public void pulsarClient_missingServiceUrl_throwsException() {
        // Missing the service URL in config so will cause an error
        Map<String, Object> clientConfig = new HashMap<>();
        when(mockClientProperties.getClientConfig()).thenReturn(clientConfig);

        // We expect an exception from pulsarClient(...) call
        ThrowingRunnable r = () -> pulsarConfig.pulsarClient(mockClientProperties);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, r);

        String expectedMessage = "service URL or service URL provider needs to be specified";
        assertTrue("Exception message should contain the expected error text",
                exception.getMessage().contains(expectedMessage));
    }

    // Test which ensures an error is thrown if pulsar producer isn't created with topicName
    @Test
    public void pulsarProducer_missingTopicName_throwsException() throws Exception {
        setUpProducerTest();
        
        when(mockProducerProperties.getProducerConfig()).thenReturn(new HashMap<>());
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn("test-pod-name");

        // Simulate exception thrown on producer creation
        when(mockProducerBuilder.create())
                .thenThrow(new IllegalArgumentException("Topic name must be set on the producer builder"));

        ThrowingRunnable r = () -> spiedConfig.pulsarProducer(mockClient, mockProducerProperties);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, r);

        String expectedErrorSubstring = "Topic name must be set on the producer builder";
        assertTrue(exception.getMessage().contains(expectedErrorSubstring));
    }

    /**
     * Environment variable is set, and user does NOT specify producerName in
     * config:
     * we should use the environment-provided name.
     */
    @Test
    public void testProducerNameFromEnvVarNoUserConfig() throws Exception {
        setUpProducerTest();
        
        final String envPodName = "NUMAFLOW_POD_VALUE";
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

        assertNotNull(producer);
        // Check that the "producerName" is set to envPodName
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    /**
     * Environment variable is set, but user explicitly sets producerName:
     * we warn and override with the environment-provided name anyway.
     */
    @Test
    public void testUserDefinedProducerNameOverridden() throws Exception {
        setUpProducerTest();
        
        final String envPodName = "my-env-pod";
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

        Map<String, Object> userConfig = new HashMap<>();
        userConfig.put("producerName", "userProvidedName");
        userConfig.put("topicName", "test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(userConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(configCaptor.capture());
        assertEquals(envPodName, configCaptor.getValue().get("producerName"));
    }

    /**
     * If NUMAFLOW_POD environment variable is not set or blank, we generate a
     * fallback name.
     */
    @Test
    public void testNoEnvVariableFoundFallbackName() throws Exception {
        setUpProducerTest();
        
        // Return null to simulate environment variable not set
        when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(null);

        Map<String, Object> emptyConfig = new HashMap<>();
        emptyConfig.put("topicName", "test-topic");
        when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

        Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

        assertNotNull(producer);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockProducerBuilder).loadConf(captor.capture());
        String actualProducerName = (String) captor.getValue().get("producerName");
        // Expect "pod-" + random UUID
        assertTrue(actualProducerName.startsWith("pod-"));
        assertNotEquals("pod-", actualProducerName);
    }
}