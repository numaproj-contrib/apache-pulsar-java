package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Sourcer;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;
import io.numaproj.numaflow.sourcer.OutputObserver;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Topics;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.partition.PartitionedTopicMetadata;
import org.apache.pulsar.common.policies.data.PartitionedTopicStats;
import org.apache.pulsar.common.policies.data.SubscriptionStats;
import org.apache.pulsar.common.policies.data.TopicStats;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PulsarSourceTest {

    private PulsarSource pulsarSource;
    private PulsarConsumerManager consumerManagerMock;
    private Consumer<byte[]> consumerMock;

    @Before
    public void setUp() {
        try {
            pulsarSource = new PulsarSource();
            consumerManagerMock = mock(PulsarConsumerManager.class);
            consumerMock = mock(Consumer.class);
            // Inject the mocked PulsarConsumerManager into pulsarSource using
            // ReflectionTestUtils.
            ReflectionTestUtils.setField(pulsarSource, "pulsarConsumerManager", consumerManagerMock);
        } catch (Exception e) {
            fail("Setup failed with exception: " + e.getMessage());
        }
    }

    @After
    public void tearDown() {
        pulsarSource = null;
        consumerManagerMock = null;
        consumerMock = null;
    }

    /**
     * Test that when messagesToAck is not empty, the read method returns early.
     */
    @Test
    public void readWhenMessagesToAckNotEmpty() {
        try {
            // Prepopulate the messagesToAck map using reflection access.
            // We simulate that there is already one message waiting for ack.
            String dummyMsgId = "dummyMsgId";
            @SuppressWarnings("unchecked")
            java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = (java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>>) ReflectionTestUtils
                    .getField(pulsarSource, "messagesToAck");
            // Create a dummy Pulsar message and add it to the map.
            @SuppressWarnings("unchecked")
            org.apache.pulsar.client.api.Message<byte[]> dummyMessage = mock(
                    org.apache.pulsar.client.api.Message.class);
            when(dummyMessage.getMessageId()).thenReturn(mock(MessageId.class));
            messagesToAck.put(dummyMsgId, dummyMessage);

            // Create mocks for ReadRequest and OutputObserver.
            ReadRequest readRequest = mock(ReadRequest.class);
            when(readRequest.getCount()).thenReturn(10L);
            when(readRequest.getTimeout()).thenReturn(Duration.ofMillis(1000));
            OutputObserver observer = mock(OutputObserver.class);

            // Call read.
            pulsarSource.read(readRequest, observer);
            // Since messagesToAck is not empty, read should return early and not call
            // consumerManager.getOrCreateConsumer.
            verify(consumerManagerMock, never()).getOrCreateConsumer(anyLong(), anyLong());
            verify(observer, never()).send(any(Message.class));
        } catch (PulsarClientException e) {
            fail("Unexpected PulsarClientException thrown in testReadWhenMessagesToAckNotEmpty: " + e.getMessage());
        }
    }

    /**
     * Test the normal behavior of read when batchReceive returns no messages.
     */
    @Test
    public void readWhenNoMessagesReceived() {
        try {
            // Reset the messagesToAck map to ensure it is empty.
            @SuppressWarnings("unchecked")
            java.util.Map<String, ?> messagesToAck = (java.util.Map<String, ?>) ReflectionTestUtils
                    .getField(pulsarSource, "messagesToAck");
            messagesToAck.clear();

            // Stub the consumerManager to return the consumerMock.
            when(consumerManagerMock.getOrCreateConsumer(10L, 1000L)).thenReturn(consumerMock);
            // Simulate batchReceive returning null.
            when(consumerMock.batchReceive()).thenReturn(null);

            ReadRequest readRequest = mock(ReadRequest.class);
            when(readRequest.getCount()).thenReturn(10L);
            when(readRequest.getTimeout()).thenReturn(Duration.ofMillis(1000));

            OutputObserver observer = mock(OutputObserver.class);

            pulsarSource.read(readRequest, observer);

            // Verify that observer.send is never called.
            verify(observer, never()).send(any(Message.class));
        } catch (PulsarClientException e) {
            fail("Unexpected PulsarClientException thrown in testReadWhenNoMessagesReceived: " + e.getMessage());
        }
    }

    /**
     * Test the normal behavior of read when batchReceive returns some messages.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void readWhenMessagesReceived() {
        try {
            // Clear messagesToAck
            java.util.Map<String, ?> messagesToAck = (java.util.Map<String, ?>) ReflectionTestUtils
                    .getField(pulsarSource, "messagesToAck");
            messagesToAck.clear();

            // Setup a fake batch of messages
            org.apache.pulsar.client.api.Message<byte[]> msg1 = mock(org.apache.pulsar.client.api.Message.class);
            org.apache.pulsar.client.api.Message<byte[]> msg2 = mock(org.apache.pulsar.client.api.Message.class);

            // Stub message ids and values.
            MessageId msgId1 = mock(MessageId.class);
            MessageId msgId2 = mock(MessageId.class);
            when(msgId1.toString()).thenReturn("msg1");
            when(msgId2.toString()).thenReturn("msg2");
            when(msg1.getMessageId()).thenReturn(msgId1);
            when(msg2.getMessageId()).thenReturn(msgId2);
            when(msg1.getValue()).thenReturn("Hello".getBytes(StandardCharsets.UTF_8));
            when(msg2.getValue()).thenReturn("World".getBytes(StandardCharsets.UTF_8));
            
            // Stub metadata methods required by buildHeaders()
            when(msg1.getProducerName()).thenReturn("test-producer");
            when(msg2.getProducerName()).thenReturn("test-producer");
            when(msg1.getTopicName()).thenReturn("test-topic");
            when(msg2.getTopicName()).thenReturn("test-topic");
            when(msg1.getPublishTime()).thenReturn(1000L);
            when(msg2.getPublishTime()).thenReturn(2000L);
            when(msg1.getEventTime()).thenReturn(1000L);
            when(msg2.getEventTime()).thenReturn(2000L);
            when(msg1.getRedeliveryCount()).thenReturn(0);
            when(msg2.getRedeliveryCount()).thenReturn(0);
            when(msg1.getProperties()).thenReturn(Collections.emptyMap());
            when(msg2.getProperties()).thenReturn(Collections.emptyMap());

            // Create a fake Messages<byte[]> object
            Messages<byte[]> messages = mock(Messages.class);
            when(messages.size()).thenReturn(2);
            java.util.List<org.apache.pulsar.client.api.Message<byte[]>> messageList = Arrays.asList(msg1, msg2);
            when(messages.iterator()).thenReturn(messageList.iterator());

            // Stub consumerManager and consumer behavior.
            when(consumerManagerMock.getOrCreateConsumer(10L, 1000L)).thenReturn(consumerMock);
            when(consumerMock.batchReceive()).thenReturn(messages);

            // Create a fake ReadRequest and OutputObserver.
            ReadRequest readRequest = mock(ReadRequest.class);
            when(readRequest.getCount()).thenReturn(10L);
            when(readRequest.getTimeout()).thenReturn(Duration.ofMillis(1000));
            OutputObserver observer = mock(OutputObserver.class);

            pulsarSource.read(readRequest, observer);

            // Verify that observer.send is called for each received message.
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(observer, times(2)).send(messageCaptor.capture());
            java.util.List<Message> sentMessages = messageCaptor.getAllValues();
            assertEquals(2, sentMessages.size());
            
            // Validate contents of messages using getValue().
            assertEquals("Hello", new String(sentMessages.get(0).getValue(), StandardCharsets.UTF_8));
            assertEquals("World", new String(sentMessages.get(1).getValue(), StandardCharsets.UTF_8));

            // Verify headers are correctly populated for first message
            Message firstMessage = sentMessages.get(0);
            assertNotNull("Headers should not be null", firstMessage.getHeaders());
            assertEquals("test-producer", firstMessage.getHeaders().get("x-pulsar-producer-name"));
            assertEquals("msg1", firstMessage.getHeaders().get("x-pulsar-message-id"));
            assertEquals("test-topic", firstMessage.getHeaders().get("x-pulsar-topic-name"));
            assertEquals("1000", firstMessage.getHeaders().get("x-pulsar-publish-time"));
            assertEquals("1000", firstMessage.getHeaders().get("x-pulsar-event-time"));
            assertEquals("0", firstMessage.getHeaders().get("x-pulsar-redelivery-count"));

            // Verify headers are correctly populated for second message
            Message secondMessage = sentMessages.get(1);
            assertNotNull("Headers should not be null", secondMessage.getHeaders());
            assertEquals("test-producer", secondMessage.getHeaders().get("x-pulsar-producer-name"));
            assertEquals("msg2", secondMessage.getHeaders().get("x-pulsar-message-id"));
            assertEquals("test-topic", secondMessage.getHeaders().get("x-pulsar-topic-name"));
            assertEquals("2000", secondMessage.getHeaders().get("x-pulsar-publish-time"));
            assertEquals("2000", secondMessage.getHeaders().get("x-pulsar-event-time"));
            assertEquals("0", secondMessage.getHeaders().get("x-pulsar-redelivery-count"));

            // Confirm messages are tracked for ack.
            // The keys should be "msg1" and "msg2"
            java.util.Map<String, ?> ackMap = (java.util.Map<String, ?>) ReflectionTestUtils.getField(pulsarSource,
                    "messagesToAck");
            assertTrue(ackMap.containsKey("msg1"));
            assertTrue(ackMap.containsKey("msg2"));
        } catch (PulsarClientException e) {
            fail("Unexpected PulsarClientException thrown in testReadWhenMessagesReceived: " + e.getMessage());
        }
    }

    /**
     * Test that custom message properties are included in headers.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void readWhenMessagesWithCustomProperties() {
        try {
            // Clear messagesToAck
            java.util.Map<String, ?> messagesToAck = (java.util.Map<String, ?>) ReflectionTestUtils
                    .getField(pulsarSource, "messagesToAck");
            messagesToAck.clear();

            // Setup a message with custom properties
            org.apache.pulsar.client.api.Message<byte[]> msg = mock(org.apache.pulsar.client.api.Message.class);
            MessageId msgId = mock(MessageId.class);
            when(msgId.toString()).thenReturn("msg1");
            when(msg.getMessageId()).thenReturn(msgId);
            when(msg.getValue()).thenReturn("Test".getBytes(StandardCharsets.UTF_8));
            
            // Stub metadata methods
            when(msg.getProducerName()).thenReturn("test-producer");
            when(msg.getTopicName()).thenReturn("test-topic");
            when(msg.getPublishTime()).thenReturn(1000L);
            when(msg.getEventTime()).thenReturn(1000L);
            when(msg.getRedeliveryCount()).thenReturn(0);
            
            // Add custom properties
            Map<String, String> customProps = new HashMap<>();
            customProps.put("custom-key-1", "custom-value-1");
            customProps.put("custom-key-2", "custom-value-2");
            customProps.put("app-version", "1.2.3");
            when(msg.getProperties()).thenReturn(customProps);

            // Create a fake Messages object
            Messages<byte[]> messages = mock(Messages.class);
            when(messages.size()).thenReturn(1);
            when(messages.iterator()).thenReturn(Collections.singletonList(msg).iterator());

            // Stub consumerManager and consumer behavior
            when(consumerManagerMock.getOrCreateConsumer(10L, 1000L)).thenReturn(consumerMock);
            when(consumerMock.batchReceive()).thenReturn(messages);

            // Create request and observer
            ReadRequest readRequest = mock(ReadRequest.class);
            when(readRequest.getCount()).thenReturn(10L);
            when(readRequest.getTimeout()).thenReturn(Duration.ofMillis(1000));
            OutputObserver observer = mock(OutputObserver.class);

            pulsarSource.read(readRequest, observer);

            // Capture the sent message
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(observer, times(1)).send(messageCaptor.capture());
            Message sentMessage = messageCaptor.getValue();

            // Verify standard headers are present
            assertNotNull("Headers should not be null", sentMessage.getHeaders());
            assertEquals("test-producer", sentMessage.getHeaders().get("x-pulsar-producer-name"));
            assertEquals("msg1", sentMessage.getHeaders().get("x-pulsar-message-id"));
            
            // Verify custom properties are included in headers
            assertEquals("custom-value-1", sentMessage.getHeaders().get("custom-key-1"));
            assertEquals("custom-value-2", sentMessage.getHeaders().get("custom-key-2"));
            assertEquals("1.2.3", sentMessage.getHeaders().get("app-version"));

        } catch (PulsarClientException e) {
            fail("Unexpected PulsarClientException thrown: " + e.getMessage());
        }
    }

    /**
     * Test the ack method when there is a message to be acknowledged.
     */
    @Test
    public void ackSuccessful() {
        try {
            // Create a dummy message to acknowledge.
            org.apache.pulsar.client.api.Message<byte[]> msg = mock(org.apache.pulsar.client.api.Message.class);
            MessageId msgId = mock(MessageId.class);
            when(msgId.toString()).thenReturn("ackMsg");
            when(msg.getMessageId()).thenReturn(msgId);
            when(msg.getValue()).thenReturn("AckPayload".getBytes(StandardCharsets.UTF_8));

            // Insert the dummy message into the messagesToAck map.
            @SuppressWarnings("unchecked")
            java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = (java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>>) ReflectionTestUtils
                    .getField(pulsarSource, "messagesToAck");
            messagesToAck.clear();
            messagesToAck.put("ackMsg", msg);

            // Stub consumerManager to return consumerMock for the ack call.
            when(consumerManagerMock.getOrCreateConsumer(0, 0)).thenReturn(consumerMock);

            // Create a fake AckRequest with an offset corresponding to the message id.
            AckRequest ackRequest = new AckRequest() {
                @Override
                public java.util.List<Offset> getOffsets() {
                    return Collections.singletonList(new Offset("ackMsg".getBytes(StandardCharsets.UTF_8)));
                }
            };

            pulsarSource.ack(ackRequest);

            // Verify that consumer.acknowledge is called on the message.
            verify(consumerMock, times(1)).acknowledge(msg);
            // Verify that the messagesToAck map is now empty.
            assertFalse(messagesToAck.containsKey("ackMsg"));
        } catch (PulsarClientException e) {
            fail("Unexpected PulsarClientException thrown in testAckSuccessful: " + e.getMessage());
        }
    }

    /**
     * Test the ack method when the offset does not exist in messagesToAck.
     */
    @Test
    public void ackNoMatchingMessage() throws PulsarClientException {
        // Ensure messagesToAck is empty.
        java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = (java.util.Map<String, org.apache.pulsar.client.api.Message<byte[]>>) ReflectionTestUtils
                .getField(pulsarSource, "messagesToAck");
        messagesToAck.clear();

        AckRequest ackRequest = new AckRequest() {
            @Override
            public java.util.List<Offset> getOffsets() {
                return Collections.singletonList(new Offset("nonExistentMsg".getBytes(StandardCharsets.UTF_8)));
            }
        };

        pulsarSource.ack(ackRequest);

        // Verify that consumerManager.getOrCreateConsumer is never called.
        try {
            verify(consumerManagerMock, never()).getOrCreateConsumer(anyLong(), anyLong());
        } catch (PulsarClientException e) {
            fail("Unexpected exception during verification in testAckNoMatchingMessage: " + e.getMessage());
        }
    }

    /**
     * Tests that the correct backlog is returned for partitioned topics with
     * subscription at partitioned level.
     */
    @Test
    public void getPendingPartitionedTopic() {
        PulsarConsumerProperties mockProperties = mock(PulsarConsumerProperties.class);
        PulsarAdmin mockAdmin = mock(PulsarAdmin.class);
        Topics mockTopics = mock(Topics.class);

        Set<String> topicNames = new HashSet<>();
        topicNames.add("persistent://tenant/namespace/topic");
        String subscriptionName = "test-subscription";

        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put("topicNames", topicNames);
        consumerConfig.put("subscriptionName", subscriptionName);

        PartitionedTopicMetadata metadata = new PartitionedTopicMetadata();
        metadata.partitions = 3;

        PartitionedTopicStats partitionedStats = mock(PartitionedTopicStats.class);
        Map<String, SubscriptionStats> subscriptions = new HashMap<>();
        SubscriptionStats subscriptionStats = mock(SubscriptionStats.class);
        subscriptions.put(subscriptionName, subscriptionStats);

        // Configure mocks
        when(mockProperties.getConsumerConfig()).thenReturn(consumerConfig);
        when(mockAdmin.topics()).thenReturn(mockTopics);
        try {
            when(mockTopics.getPartitionedTopicMetadata(anyString())).thenReturn(metadata);
            when(mockTopics.getPartitionedStats(anyString(), anyBoolean())).thenReturn(partitionedStats);
            @SuppressWarnings("unchecked")
            Map<String, SubscriptionStats> castedSubscriptions = (Map<String, SubscriptionStats>) (Map<?, ?>) subscriptions;
            when(partitionedStats.getSubscriptions()).thenReturn((Map) castedSubscriptions);
            when(subscriptionStats.getMsgBacklog()).thenReturn(100L);

            // Use reflection to set private fields
            Field pulsarConsumerPropertiesField = PulsarSource.class.getDeclaredField("pulsarConsumerProperties");
            pulsarConsumerPropertiesField.setAccessible(true);
            pulsarConsumerPropertiesField.set(pulsarSource, mockProperties);

            Field pulsarAdminField = PulsarSource.class.getDeclaredField("pulsarAdmin");
            pulsarAdminField.setAccessible(true);
            pulsarAdminField.set(pulsarSource, mockAdmin);

            // Act
            long result = pulsarSource.getPending();

            // Assert
            assertEquals(100L, result);
            verify(mockTopics).getPartitionedTopicMetadata(anyString());
            verify(mockTopics).getPartitionedStats(anyString(), eq(false));
            verify(subscriptionStats).getMsgBacklog();

        } catch (PulsarAdminException | NoSuchFieldException | IllegalAccessException e) {
            fail("Unexpected exception in getPendingPartitionedTopic: " + e.getMessage());
        }

    }

    // Returns backlog count for a non-partitioned topic
    @Test
    public void getPendingNonPartitionedTopic() {
        PulsarAdmin mockPulsarAdmin = mock(PulsarAdmin.class);
        Topics mockTopics = mock(Topics.class);
        PulsarConsumerProperties mockProperties = mock(PulsarConsumerProperties.class);

        Map<String, Object> consumerConfig = new HashMap<>();
        Set<String> topicNames = new HashSet<>();
        topicNames.add("test-topic");
        consumerConfig.put("topicNames", topicNames);
        consumerConfig.put("subscriptionName", "test-subscription");

        when(mockProperties.getConsumerConfig()).thenReturn(consumerConfig);
        when(mockPulsarAdmin.topics()).thenReturn(mockTopics);

        // Mock partitioned topic metadata with 0 partitions (non-partitioned)
        PartitionedTopicMetadata metadata = new PartitionedTopicMetadata();
        metadata.partitions = 0;
        try {
            when(mockTopics.getPartitionedTopicMetadata("test-topic")).thenReturn(metadata);

            TopicStats mockTopicStats = mock(TopicStats.class);
            SubscriptionStats mockSubStats = mock(SubscriptionStats.class);
            Map<String, SubscriptionStats> subscriptions = new HashMap<>();
            subscriptions.put("test-subscription", mockSubStats);

            when(mockTopics.getStats("test-topic")).thenReturn(mockTopicStats);
            when(mockTopicStats.getSubscriptions()).thenReturn((Map) subscriptions);
            when(mockSubStats.getMsgBacklog()).thenReturn(100L);

            PulsarSource pulsarSource = new PulsarSource();
            ReflectionTestUtils.setField(pulsarSource, "pulsarAdmin", mockPulsarAdmin);
            ReflectionTestUtils.setField(pulsarSource, "pulsarConsumerProperties", mockProperties);

            long result = pulsarSource.getPending();

            assertEquals(100L, result);
            verify(mockTopics).getPartitionedTopicMetadata("test-topic");
            verify(mockTopics).getStats("test-topic");

        } catch (PulsarAdminException e) {
            fail("Unexpected PulsarAdminException thrown in getPendingNonPartitionedTopic: " + e.getMessage());
        }

    }

    /**
     * Tests that the method returns a list of partition indexes from 0 to
     * numPartitions-1 for a partitioned topic.
     */
    @Test
    public void getPartitionsPartitionedTopic() {
        PulsarConsumerProperties mockProperties = mock(PulsarConsumerProperties.class);
        Map<String, Object> consumerConfig = new HashMap<>();
        String topicName = "test-topic";
        Set<String> topicNames = Set.of(topicName);
        consumerConfig.put("topicNames", topicNames);
        when(mockProperties.getConsumerConfig()).thenReturn(consumerConfig);

        PulsarAdmin mockAdmin = mock(PulsarAdmin.class);
        Topics mockTopics = mock(Topics.class);
        when(mockAdmin.topics()).thenReturn(mockTopics);

        PartitionedTopicMetadata metadata = new PartitionedTopicMetadata();
        metadata.partitions = 3;
        try {
            when(mockTopics.getPartitionedTopicMetadata(topicName)).thenReturn(metadata);

            // Use reflection to set private fields
            ReflectionTestUtils.setField(pulsarSource, "pulsarConsumerProperties", mockProperties);
            ReflectionTestUtils.setField(pulsarSource, "pulsarAdmin", mockAdmin);

            List<Integer> result = pulsarSource.getPartitions();

            assertEquals(3, result.size());
            assertEquals(List.of(0, 1, 2), result);

            verify(mockTopics).getPartitionedTopicMetadata(topicName);

        } catch (PulsarAdminException e) {
            fail("Unexpected PulsarAdminException thrown in getPartitionsPartitionedTopic: " + e.getMessage());
        }

    }

    /**
     * Tests that a non-partitioned topic (numPartitions < 1) returns a singleton
     * list containing 0.
     */
    @Test
    public void getPartitionsNonPartitionedTopic() {
        PulsarAdmin pulsarAdmin = mock(PulsarAdmin.class);
        Topics topics = mock(Topics.class);
        when(pulsarAdmin.topics()).thenReturn(topics);
        PartitionedTopicMetadata metadata = new PartitionedTopicMetadata();
        metadata.partitions = 0;
        try {
            when(topics.getPartitionedTopicMetadata(anyString())).thenReturn(metadata);
        } catch (PulsarAdminException e) {
            fail("Unexpected PulsarAdminException thrown: " + e.getMessage());
        }

        PulsarConsumerProperties pulsarConsumerProperties = mock(PulsarConsumerProperties.class);
        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put("topicNames", Set.of("test-topic"));
        when(pulsarConsumerProperties.getConsumerConfig()).thenReturn(consumerConfig);

        PulsarSource pulsarSource = new PulsarSource();
        List<Integer> partitions = pulsarSource.getPartitions();

        assertEquals(List.of(0), partitions);
    }

    /**
     * Tests that an exception causes the method to fall back to
     * defaultPartitions().
     */
    @Test
    public void getPartitionsException() {
        // Arrange
        PulsarConsumerProperties mockProperties = mock(PulsarConsumerProperties.class);
        when(mockProperties.getConsumerConfig()).thenThrow(new RuntimeException("Test exception"));

        PulsarAdmin mockAdmin = mock(PulsarAdmin.class);

        // Mock the static method defaultPartitions()
        try (MockedStatic<Sourcer> mockedSourcer = mockStatic(Sourcer.class)) {
            mockedSourcer.when(Sourcer::defaultPartitions).thenReturn(List.of(42));

            // Use reflection to set private fields
            ReflectionTestUtils.setField(pulsarSource, "pulsarConsumerProperties", mockProperties);
            ReflectionTestUtils.setField(pulsarSource, "pulsarAdmin", mockAdmin);

            List<Integer> result = pulsarSource.getPartitions();
            assertEquals(List.of(42), result);
            mockedSourcer.verify(Sourcer::defaultPartitions);
        }
    }

}
