package io.numaproj.pulsar.numaflow;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.ResponseList;
import io.numaproj.pulsar.config.producer.PulsarProducerProperties;
import io.numaproj.pulsar.producer.PulsarSink;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SchemaSerializationException;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class PulsarSinkTest {

    private interface ByteProducer extends Producer<byte[]> {
    }

    @Test
    public void processMessages_responseSuccess() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarClient mockClient = mock(PulsarClient.class);
        PulsarProducerProperties props = new PulsarProducerProperties();
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mockClient, props);

        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum = mock(Datum.class);

        byte[] testMessage = "test message".getBytes();
        when(mockDatum.getValue()).thenReturn(testMessage);
        when(mockDatum.getId()).thenReturn("msg-1");
        when(mockIterator.next()).thenReturn(mockDatum, (Datum) null);

        CompletableFuture<MessageId> future = CompletableFuture.completedFuture(mock(MessageId.class));
        when(mockProducer.sendAsync(testMessage)).thenReturn(future);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).sendAsync(testMessage);
        assertEquals(1, response.getResponses().size());
        assertTrue(response.getResponses().get(0).getSuccess());
        assertEquals("msg-1", response.getResponses().get(0).getId());
    }

    @Test
    public void processMessages_responseFailure_datumInterrupted() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mock(PulsarClient.class), new PulsarProducerProperties());

        DatumIterator mockIterator = mock(DatumIterator.class);

        when(mockIterator.next())
                .thenThrow(new InterruptedException())
                .thenReturn(null);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer, never()).sendAsync(any());
        assertTrue(response.getResponses().isEmpty());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    public void processMessages_responseFailure_addResponse() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarProducerProperties producerProperties = new PulsarProducerProperties();
        producerProperties.setDropInvalidMessages(false);
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mock(PulsarClient.class), producerProperties);

        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum = mock(Datum.class);

        byte[] testMessage = "test message".getBytes();

        when(mockDatum.getValue()).thenReturn(testMessage);
        when(mockDatum.getId()).thenReturn("msg-1");

        when(mockIterator.next()).thenReturn(mockDatum, (Datum) null);

        String exceptionMessage = "Sending failed due to network error";
        CompletableFuture<MessageId> future = new CompletableFuture<>();
        future.completeExceptionally(new PulsarClientException(exceptionMessage));
        when(mockProducer.sendAsync(testMessage)).thenReturn(future);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).sendAsync(testMessage);

        assertEquals(1, response.getResponses().size());
        assertFalse(response.getResponses().get(0).getSuccess());
        assertEquals("msg-1", response.getResponses().get(0).getId());
        assertTrue(response.getResponses().get(0).getErr().contains(exceptionMessage));
    }

    @Test
    public void producer_cleanup() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarClient mockPulsarClient = mock(PulsarClient.class);
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mockPulsarClient, new PulsarProducerProperties());

        pulsarSink.cleanup();

        verify(mockProducer).flush();
        verify(mockProducer).close();
        verify(mockPulsarClient).close();
    }

    @Test
    public void processMessages_responsePartialSuccess() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarProducerProperties producerProperties = new PulsarProducerProperties();
        producerProperties.setDropInvalidMessages(false);
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mock(PulsarClient.class), producerProperties);

        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum1 = mock(Datum.class);
        Datum mockDatum2 = mock(Datum.class);

        byte[] testMessage1 = "message part 1".getBytes();
        byte[] testMessage2 = "message part 2".getBytes();

        when(mockDatum1.getValue()).thenReturn(testMessage1);
        when(mockDatum1.getId()).thenReturn("msg-1");
        when(mockDatum2.getValue()).thenReturn(testMessage2);
        when(mockDatum2.getId()).thenReturn("msg-2");

        when(mockIterator.next()).thenReturn(mockDatum1, mockDatum2, (Datum) null);

        CompletableFuture<MessageId> successFuture = CompletableFuture.completedFuture(mock(MessageId.class));

        String exceptionMessage = "Sending failed due to network error";
        CompletableFuture<MessageId> failureFuture = new CompletableFuture<>();
        failureFuture.completeExceptionally(new PulsarClientException(exceptionMessage));

        when(mockProducer.sendAsync(testMessage1)).thenReturn(successFuture);
        when(mockProducer.sendAsync(testMessage2)).thenReturn(failureFuture);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).sendAsync(testMessage1);
        verify(mockProducer).sendAsync(testMessage2);

        assertEquals(2, response.getResponses().size());
        assertTrue(response.getResponses().get(0).getSuccess());
        assertEquals("msg-1", response.getResponses().get(0).getId());
        assertFalse(response.getResponses().get(1).getSuccess());
        assertEquals("msg-2", response.getResponses().get(1).getId());
        assertTrue(response.getResponses().get(1).getErr().contains(exceptionMessage));
    }

    @Test
    public void processMessages_dropInvalidMessagesTrue_asyncSchemaSerializationException_dropsMessage() throws Exception {
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarProducerProperties producerProperties = new PulsarProducerProperties();
        producerProperties.setDropInvalidMessages(true);
        PulsarSink pulsarSink = new PulsarSink(mockProducer, mock(PulsarClient.class), producerProperties);

        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum = mock(Datum.class);

        byte[] testMessage = "invalid".getBytes();
        when(mockDatum.getValue()).thenReturn(testMessage);
        when(mockDatum.getId()).thenReturn("msg-schema");
        when(mockIterator.next()).thenReturn(mockDatum, (Datum) null);

        CompletableFuture<MessageId> future = new CompletableFuture<>();
        future.completeExceptionally(new CompletionException(new SchemaSerializationException("Incompatible schema")));
        when(mockProducer.sendAsync(testMessage)).thenReturn(future);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).sendAsync(testMessage);
        assertEquals(1, response.getResponses().size());
        assertTrue("Expected message to be dropped (async schema error)", response.getResponses().get(0).getSuccess());
        assertEquals("msg-schema", response.getResponses().get(0).getId());
    }
}
