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
    // in-valid key-value pairs are skipped, and do not throw an error
    @Test
    public void pulsarClient_validConfig() throws Exception {
        PulsarClientProperties properties = mock(PulsarClientProperties.class);
        Map<String, Object> config = new HashMap<>();
        config.put("serviceUrl", "pulsar://test:1234"); // URL must include the protocol (pulsar:// or pulsar+ssl://) in order to be valid
        when(properties.getClientConfig()).thenReturn(config);

        PulsarConfig pulsarConfig = new PulsarConfig();
        PulsarClient client = pulsarConfig.pulsarClient(properties);

        assertNotNull(client);
        verify(properties).getClientConfig();
    }

    // Successfully create Producer bean with valid configuration properties
    @Test
    public void pulsarProducer_validConfig() throws Exception {

        // Create Client for producer 
        PulsarClientProperties clientProperties = mock(PulsarClientProperties.class);
        PulsarConfig pulsarConfig = spy(new PulsarConfig()); // Bypasses logic that a PulsarClient requires a service URL
        PulsarClient mockClient = mock(PulsarClient.class);
        doReturn(mockClient).when(pulsarConfig).pulsarClient(any(PulsarClientProperties.class));


        PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);

        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "test-topic"); 
        when(producerProperties.getProducerConfig()).thenReturn(producerConfig);
    
        @SuppressWarnings("unchecked") //Surpress warnings about unchecked type cast from ProducerBuilder to ProducerBuilder<byte[]>,
        ProducerBuilder<byte[]> mockProducerBuilder = (ProducerBuilder<byte[]>) mock(ProducerBuilder.class);
        Producer<byte[]> mockProducer = mock(Producer.class);

        when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);

        PulsarClient client = pulsarConfig.pulsarClient(clientProperties); // Will return the mock client
        Producer<byte[]> producer = pulsarConfig.pulsarProducer(client, producerProperties); // Use mock client to create the producer 

        assertNotNull("Producer should be created", producer);
                verify(mockProducerBuilder).loadConf(argThat(map -> 
            "test-topic".equals(map.get("topicName"))
        ));
        verify(mockProducerBuilder).create();
        verify(producerProperties).getProducerConfig();
    }
}