package io.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.junit.Test;

import io.numaproj.pulsar.config.PulsarClientProperties;
import io.numaproj.pulsar.config.PulsarProducerProperties;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class PulsarConfigTest {
    // Successfully create PulsarClient bean with valid configuration properties
    // note: in-valid key-value pairs are skipped, do not throw an error, and
    // therefore do not need test
    @Test
    public void pulsarClient_validConfig() throws Exception {
        PulsarClientProperties properties = mock(PulsarClientProperties.class);
        Map<String, Object> config = new HashMap<>();

        // URL must include the protocol (pulsar:// or pulsar+ssl://)
        config.put("serviceUrl", "pulsar://test:1234");

        when(properties.getClientConfig()).thenReturn(config);

        PulsarConfig pulsarConfig = new PulsarConfig();
        PulsarClient client = pulsarConfig.pulsarClient(properties);

        assertNotNull(client);
        verify(properties).getClientConfig();
    }

    // Successfully create Producer bean with valid configuration properties
    @Test
    public void pulsarProducer_validConfig() throws Exception {

        PulsarClientProperties clientProperties = mock(PulsarClientProperties.class);

        // Use spy to use the real implementation of PulsarConfig, but allows overriding
        // and tracking methods
        PulsarConfig pulsarConfig = spy(new PulsarConfig());
        PulsarClient mockClient = mock(PulsarClient.class);

        // Configures the pulsarConfig spy to return the ‘mockClient’ when the method
        // pulsarClient is called with any instance of PulsarClientProperties.
        doReturn(mockClient).when(pulsarConfig).pulsarClient(any(PulsarClientProperties.class));

        PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "test-topic");
        when(producerProperties.getProducerConfig()).thenReturn(producerConfig);

        // Surpress warnings about unchecked type cast from ProducerBuilder to ProducerBuilder<byte[]>
        @SuppressWarnings("unchecked")
        ProducerBuilder<byte[]> mockProducerBuilder = (ProducerBuilder<byte[]>) mock(ProducerBuilder.class);
        Producer<byte[]> mockProducer = mock(Producer.class);

        when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);

        PulsarClient client = pulsarConfig.pulsarClient(clientProperties); // Will return the mock client
        Producer<byte[]> producer = pulsarConfig.pulsarProducer(client, producerProperties);

        assertNotNull("Producer should be created", producer);
        verify(mockProducerBuilder).loadConf(argThat(map -> "test-topic".equals(map.get("topicName"))));
        verify(mockProducerBuilder).create();
        verify(producerProperties).getProducerConfig();
    }

    // Ensures an error is thrown if pulsar client isn't created with service url
    @Test
    public void pulsarClient_missingServiceUrl_throwsException() {
        PulsarClientProperties properties = new PulsarClientProperties();
        Map<String, Object> clientConfig = new HashMap<>(); // no service URL added to cause error
        properties.setClientConfig(clientConfig);

        PulsarConfig pulsarConfig = new PulsarConfig();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pulsarConfig.pulsarClient(properties);
        });

        String expectedMessage = "service URL or service URL provider needs to be specified";
        assertTrue("Exception message should contain the expected error text",
                exception.getMessage().contains(expectedMessage));
    }

    // Ensures an error is thrown if pulsar producer isn't created withvtopicName
    @Test
    public void pulsarProducer_missingTopicName_throwsException() throws Exception {
        PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);
        PulsarClient pulsarClient = mock(PulsarClient.class);
        @SuppressWarnings("unchecked")
        ProducerBuilder<byte[]> producerBuilder = mock(ProducerBuilder.class);
        PulsarConfig pulsarConfig = new PulsarConfig();

        when(pulsarClient.newProducer(Schema.BYTES)).thenReturn(producerBuilder);
        when(producerBuilder.loadConf(any())).thenReturn(producerBuilder);
        when(producerBuilder.create()) // mocking that anytime the producer is created, it throws an exception
                .thenThrow(new IllegalArgumentException("Topic name must be set on the producer builder"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pulsarConfig.pulsarProducer(pulsarClient, producerProperties));

        assertTrue(exception.getMessage().contains("Topic name must be set on the producer builder"));
    }
}