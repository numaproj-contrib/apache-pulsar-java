
// package io.numaproj.pulsar.config.producer;

// import org.apache.pulsar.client.api.Producer;
// import org.apache.pulsar.client.api.ProducerBuilder;
// import org.apache.pulsar.client.api.PulsarClient;
// import org.apache.pulsar.client.api.Schema;
// import org.junit.After;
// import org.junit.Before;
// import org.junit.Test;
// import org.mockito.ArgumentCaptor;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.core.env.Environment;
// import org.springframework.test.util.ReflectionTestUtils;

// import io.numaproj.pulsar.config.client.PulsarClientConfig;
// import io.numaproj.pulsar.config.client.PulsarClientProperties;

// import static org.junit.Assert.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyMap;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.argThat;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.*;

// import java.util.HashMap;
// import java.util.Map;

// @SpringBootTest(classes = PulsarProducerConfig.class)
// public class PulsarProducerConfigTest {

//     private PulsarProducerConfig pulsarProducerConfig;
//     private Environment mockEnvironment;

//     // Objects used only by specific test groups
//     private PulsarProducerConfig spiedConfig;
//     private PulsarClient mockClient;
//     private PulsarProducerProperties mockProducerProperties;
//     private ProducerBuilder<byte[]> mockProducerBuilder;
//     private Producer<byte[]> mockProducer;

//     @Before
//     public void setUp() throws Exception {
//         pulsarProducerConfig = new PulsarProducerConfig();
//         mockEnvironment = mock(Environment.class);
//         ReflectionTestUtils.setField(pulsarProducerConfig, "env", mockEnvironment);

//         mockProducerProperties = mock(PulsarProducerProperties.class);
//         mockClient = mock(PulsarClient.class);

//         spiedConfig = spy(pulsarProducerConfig);
//         PulsarClientConfig mockClientConfig = mock(PulsarClientConfig.class);
//         doReturn(mockClient).when(mockClientConfig).pulsarClient(any(PulsarClientProperties.class));

//         @SuppressWarnings("unchecked")
//         ProducerBuilder<byte[]> builder = mock(ProducerBuilder.class);
//         mockProducerBuilder = builder;

//         mockProducer = mock(Producer.class);

//         when(mockClient.newProducer(Schema.BYTES)).thenReturn(mockProducerBuilder);
//         when(mockProducerBuilder.create()).thenReturn(mockProducer);
//         when(mockProducerBuilder.loadConf(anyMap())).thenReturn(mockProducerBuilder);
//     }

//     @After
//     public void tearDown() {
//         pulsarProducerConfig = null;
//         spiedConfig = null;
//         mockProducerProperties = null;
//         mockClient = null;
//         mockProducerBuilder = null;
//         mockProducer = null;
//         mockEnvironment = null;
//     }
//     // Test to successfully create Producer bean with valid configuration properties
//     @Test
//     public void pulsarProducer_validConfig() throws Exception {
//         Map<String, Object> producerConfig = new HashMap<>();
//         producerConfig.put("topicName", "test-topic");
//         when(mockProducerProperties.getProducerConfig()).thenReturn(producerConfig);

//         Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

//         assertNotNull("Producer should be created", producer);

//         verify(mockProducerBuilder).loadConf(argThat(map -> "test-topic".equals(map.get("topicName"))));
//         verify(mockProducerBuilder).create();
//         verify(mockProducerProperties).getProducerConfig();
//     }

//     // Test which ensures an error is thrown if pulsar producer isn't created with
//     // topicName
//     @Test
//     public void pulsarProducer_missingTopicName_throwsException() throws Exception {
//         when(mockProducerProperties.getProducerConfig()).thenReturn(new HashMap<>());

//         String expectedErrorSubstring = "Topic name must be set on the producer builder";
//         when(mockProducerBuilder.create())
//                 .thenThrow(new IllegalArgumentException(expectedErrorSubstring));

//         IllegalArgumentException exception = assertThrows(
//                 IllegalArgumentException.class,
//                 () -> pulsarProducerConfig.pulsarProducer(mockClient, mockProducerProperties));

//         assertTrue(exception.getMessage().contains(expectedErrorSubstring));
//     }

//     // Test for environment variable is set, and user does NOT specify producerName
//     @Test
//     public void pulsarProducer_ProducerNameFromEnvVarNoUserConfig() throws Exception {
//         final String envPodName = "NUMAFLOW_POD_VALUE";
//         when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

//         Map<String, Object> emptyConfig = new HashMap<>();
//         emptyConfig.put("topicName", "test-topic");
//         when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

//         Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

//         assertNotNull(producer);
//         // Check that the "producerName" is set to envPodName
//         ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
//         verify(mockProducerBuilder).loadConf(configCaptor.capture());
//         assertEquals(envPodName, configCaptor.getValue().get("producerName"));
//     }

//     // Test for environment variable is set, but user explicitly sets producerName:
//     @Test
//     public void pulsarProducer_ProducerNameOverridden() throws Exception {
//         final String envPodName = "my-env-pod";
//         when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString())).thenReturn(envPodName);

//         Map<String, Object> userConfig = new HashMap<>();
//         userConfig.put("producerName", "userProvidedName");
//         userConfig.put("topicName", "test-topic");
//         when(mockProducerProperties.getProducerConfig()).thenReturn(userConfig);

//         Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

//         assertNotNull(producer);
//         ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
//         verify(mockProducerBuilder).loadConf(configCaptor.capture());
//         assertEquals(envPodName, configCaptor.getValue().get("producerName"));
//     }

//     // Test for if NUMAFLOW_POD environment variable is not set
//     @Test
//     public void pulsarProducer_NoEnvVariableFoundFallbackName() throws Exception {
//         // Simulate NUMAFLOW_POD not being set by returning null
//         when(mockEnvironment.getProperty(eq("NUMAFLOW_POD"), anyString()))
//             .thenAnswer(invocation -> invocation.getArgument(1));

//         Map<String, Object> emptyConfig = new HashMap<>();
//         emptyConfig.put("topicName", "test-topic");
//         when(mockProducerProperties.getProducerConfig()).thenReturn(emptyConfig);

//         Producer<byte[]> producer = spiedConfig.pulsarProducer(mockClient, mockProducerProperties);

//         assertNotNull(producer);
//         ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
//         verify(mockProducerBuilder).loadConf(captor.capture());
        
//         String producerName = (String) captor.getValue().get("producerName");
//         assertNotNull("Producer name should not be null", producerName);
//         assertTrue("Producer name should start with 'pod-'", producerName.startsWith("pod-"));
//     }
// }