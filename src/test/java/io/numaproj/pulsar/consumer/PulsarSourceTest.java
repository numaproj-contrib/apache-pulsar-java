package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.OutputObserver;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
}
