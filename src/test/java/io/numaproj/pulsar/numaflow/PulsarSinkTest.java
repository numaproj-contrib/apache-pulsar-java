package io.numaproj.pulsar.numaflow;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.ResponseList;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PulsarSinkTest {

    // Helper interface to represent Producer<byte[]> without type issues
    private interface ByteProducer extends Producer<byte[]> { }

    // Successfully process and send messages to Pulsar from DatumIterator
    @Test
    public void processMessages_responseSuccess() throws Exception {
        PulsarSink pulsarSink = new PulsarSink();
        ByteProducer mockProducer = mock(ByteProducer.class);
        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum = mock(Datum.class);

        ReflectionTestUtils.setField(pulsarSink, "producer", mockProducer);

        byte[] testMessage = "test message".getBytes();
        when(mockDatum.getValue()).thenReturn(testMessage);
        when(mockDatum.getId()).thenReturn("msg-1");
        when(mockIterator.next()).thenReturn(mockDatum, (Datum) null);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).send(testMessage);
        assertEquals(1, response.getResponses().size());
        assertTrue(response.getResponses().get(0).getSuccess());
        assertEquals("msg-1", response.getResponses().get(0).getId());
    }

    // Failed to process messages because the thread waiting for the next datum is interrupted; no new messages
    @Test
    public void processMessages_responseFailure_datumInterupted() throws Exception {
        PulsarSink pulsarSink = new PulsarSink();
        ByteProducer mockProducer = mock(ByteProducer.class);
        DatumIterator mockIterator = mock(DatumIterator.class);

        ReflectionTestUtils.setField(pulsarSink, "producer", mockProducer);

        when(mockIterator.next())
            .thenThrow(new InterruptedException())
            .thenReturn(null);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer, never()).send(any());
        assertTrue(response.getResponses().isEmpty());
        assertTrue(Thread.currentThread().isInterrupted());
    }



    // Verifies when sending a message fails, the processMessages method calls responseListBuilder.addResponse with a failure response
    @Test
    public void processMessages_responseFailure_addResponse() throws Exception {
        PulsarSink pulsarSink = new PulsarSink();
        ByteProducer mockProducer = mock(ByteProducer.class);
        DatumIterator mockIterator = mock(DatumIterator.class);
        Datum mockDatum = mock(Datum.class);

        ReflectionTestUtils.setField(pulsarSink, "producer", mockProducer);

        byte[] testMessage = "test message".getBytes();

        when(mockDatum.getValue()).thenReturn(testMessage);
        when(mockDatum.getId()).thenReturn("msg-1");

        when(mockIterator.next()).thenReturn(mockDatum, (Datum) null);

        String exceptionMessage = "Sending failed due to network error";
        doThrow(new PulsarClientException(exceptionMessage))
            .when(mockProducer).send(testMessage);

        ResponseList response = pulsarSink.processMessages(mockIterator);

        verify(mockProducer).send(testMessage);

        assertEquals(1, response.getResponses().size());
        assertFalse(response.getResponses().get(0).getSuccess());
        assertEquals("msg-1", response.getResponses().get(0).getId());
        assertEquals(exceptionMessage, response.getResponses().get(0).getErr());
    }

    

    // Ensure proper resource cleanup on shutdown
    @Test
    public void producer_cleanup() throws Exception {
        // Arrange
        PulsarSink pulsarSink = new PulsarSink();
        ByteProducer mockProducer = mock(ByteProducer.class);
        PulsarClient mockPulsarClient = mock(PulsarClient.class);

        ReflectionTestUtils.setField(pulsarSink, "producer", mockProducer);
        ReflectionTestUtils.setField(pulsarSink, "pulsarClient", mockPulsarClient);

        pulsarSink.cleanup();

        verify(mockProducer).close();
        verify(mockPulsarClient).close();
    }

}