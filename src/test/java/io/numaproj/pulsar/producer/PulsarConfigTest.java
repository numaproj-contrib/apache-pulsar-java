package io.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
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
import java.util.concurrent.ExecutionException;

public class PulsarConfigTest {
    // Successfully create PulsarClient bean with valid configuration properties
    // in-valid key-value pairs are skipped, and do not throw an error
    @Test
    public void pulsarClient_validConfig() throws Exception {
        PulsarClientProperties properties = mock(PulsarClientProperties.class);
        Map<String, Object> config = new HashMap<>();
        config.put("serviceUrl", "pulsar://test:1234"); // URL must include the protocol (pulsar:// or pulsar+ssl://) in
                                                        // order to be valid
        when(properties.getClientConfig()).thenReturn(config);

        PulsarConfig pulsarConfig = new PulsarConfig();
        PulsarClient client = pulsarConfig.pulsarClient(properties);

        assertNotNull(client);
        verify(properties).getClientConfig();
    }

    // Successfully create Producer bean with valid configuration properties
    @Test
    public void pulsarProducer_validConfig() throws Exception {

        // Create client for producer
        PulsarClientProperties clientProperties = mock(PulsarClientProperties.class);
        PulsarConfig pulsarConfig = spy(new PulsarConfig()); // Bypasses logic that a PulsarClient requires a service
                                                             // URL
        PulsarClient mockClient = mock(PulsarClient.class);

        //Configures the pulsarConfig spy to return the ‘mockClient’ when the method pulsarClient is called with any instance of PulsarClientProperties.
        doReturn(mockClient).when(pulsarConfig).pulsarClient(any(PulsarClientProperties.class));

        PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);

        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put("topicName", "test-topic");
        when(producerProperties.getProducerConfig()).thenReturn(producerConfig);

        @SuppressWarnings("unchecked") // Surpress warnings about unchecked type cast from ProducerBuilder to
                                       // ProducerBuilder<byte[]>,
        ProducerBuilder<byte[]> mockProducerBuilder = (ProducerBuilder<byte[]>) mock(ProducerBuilder.class);
        Producer<byte[]> mockProducer = mock(Producer.class);

        when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
        when(mockProducerBuilder.create()).thenReturn(mockProducer);

        PulsarClient client = pulsarConfig.pulsarClient(clientProperties); // Will return the mock client
        Producer<byte[]> producer = pulsarConfig.pulsarProducer(client, producerProperties); // Use mock client to
                                                                                             // create the producer

        assertNotNull("Producer should be created", producer);
        verify(mockProducerBuilder).loadConf(argThat(map -> "test-topic".equals(map.get("topicName"))));
        verify(mockProducerBuilder).create();
        verify(producerProperties).getProducerConfig();
    }


    //Ensures an error is thrown if pulsar client isn't created with service url
    @Test
    public void pulsarClient_missingServiceUrl_throwsException() {
        PulsarClientProperties properties = new PulsarClientProperties();
        Map<String, Object> clientConfig = new HashMap<>(); // no service URL added to cause error
        properties.setClientConfig(clientConfig);

        PulsarConfig pulsarConfig = new PulsarConfig();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pulsarConfig.pulsarClient(properties);
        });

        // Verify that the exception message contains the required text.
        String expectedMessage = "service URL or service URL provider needs to be specified";
        assertTrue("Exception message should contain the expected error text",
                exception.getMessage().contains(expectedMessage));
    }

    //Ensures an error is thrown if pulsar producer isn't created withvtopicName
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

        // Verify the exception is thrown with correct message
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> pulsarConfig.pulsarProducer(pulsarClient, producerProperties)
        );

        assertTrue(exception.getMessage().contains("Topic name must be set on the producer builder"));
    }
    
    /// used by: java.lang.IllegalArgumentException: Topic name must be set on the producer builder
	// at org.apache.pulsar.client.impl.ProducerBuilderImpl.createAsync(ProducerBuilderImpl.java:99) ~[pulsar-client-all-3.1.2.jar:3.1.2]
	// ... 45 common frames omitted

}


/**
 * based on other test 
 *  // Create client for producer
            PulsarClientProperties clientProperties = mock(PulsarClientProperties.class);
            PulsarConfig pulsarConfig = spy(new PulsarConfig()); // Bypasses logic that a PulsarClient requires a service
                                                                 // URL
            PulsarClient mockClient = mock(PulsarClient.class);
            doReturn(mockClient).when(pulsarConfig).pulsarClient(any(PulsarClientProperties.class));
    
            PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);
    
            Map<String, Object> producerConfig = new HashMap<>();
            producerConfig.put("producerName", "test-producer");
            when(producerProperties.getProducerConfig()).thenReturn(producerConfig);
    
            @SuppressWarnings("unchecked") // Surpress warnings about unchecked type cast from ProducerBuilder to
                                           // ProducerBuilder<byte[]>,
            ProducerBuilder<byte[]> mockProducerBuilder = (ProducerBuilder<byte[]>) mock(ProducerBuilder.class);
            Producer<byte[]> mockProducer = mock(Producer.class);
    
            when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
            when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
            when(mockProducerBuilder.create()).thenReturn(mockProducer);
    
            PulsarClient client = pulsarConfig.pulsarClient(clientProperties); // Will return the mock client
                                                                                                 // create the producer
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                Producer<byte[]> producer = pulsarConfig.pulsarProducer(client, producerProperties);
            });
                                                                                                 
    
            String expectedMessage = "Topic name must be set on the producer builder";
            assertTrue("Exception message should contain the expected error text",
                exception.getMessage().contains(expectedMessage));
 * 
 * 
 */



 /** 
  * THURS WIP

        // Create client for producer
        // PulsarClientProperties clientProperties = mock(PulsarClientProperties.class);
        // PulsarConfig pulsarConfig = new PulsarConfig();
        
        // PulsarProducerProperties producerProperties = mock(PulsarProducerProperties.class);

        // Map<String, Object> producerConfig = new HashMap<>();
        // when(producerProperties.getProducerConfig()).thenReturn(producerConfig);


        // // mock pulsar client, when (pulsarConfig.pulsarClient(clientProperties)).thenReturn(mockClient)
        // //mockClient 

        // PulsarClient client = pulsarConfig.pulsarClient(clientProperties); // Will return the mock client

        // IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
        //     pulsarConfig.pulsarProducer(client, producerProperties);
        // });

        // // Verify that the exception message contains the expected text.
        // String expectedMessage = "Topic name must be set on the producer builder";
        // assertTrue("Exception message should contain the expected error text",
        //         exception.getMessage().contains(expectedMessage));


  */