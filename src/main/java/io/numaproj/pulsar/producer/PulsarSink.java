package io.numaproj.pulsar.producer;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.Response;
import io.numaproj.numaflow.sinker.ResponseList;
import io.numaproj.numaflow.sinker.Server;
import io.numaproj.numaflow.sinker.Sinker;
import io.numaproj.pulsar.config.producer.PulsarProducerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SchemaSerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
public class PulsarSink extends Sinker {

    private final Producer<byte[]> producer;
    private final PulsarClient pulsarClient;
    private final PulsarProducerProperties producerProperties;


    private Server server;

    public PulsarSink(Producer<byte[]> producer, PulsarClient pulsarClient, PulsarProducerProperties producerProperties) {
        this.producer = producer;
        this.pulsarClient = pulsarClient;
        this.producerProperties = producerProperties;
    }

    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public ResponseList processMessages(DatumIterator datumIterator) {
        ResponseList.ResponseListBuilder responseListBuilder = ResponseList.newBuilder();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        while (true) {
            Datum datum;
            try {
                datum = datumIterator.next();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }
            // null means the iterator is closed, so we break
            if (datum == null) {
                break;
            }

            final byte[] msg = datum.getValue();
            final String msgId = datum.getId();

            CompletableFuture<Void> future = producer.sendAsync(msg)
                    .thenAccept(messageId -> {
                        log.atInfo().setMessage("Processed message.")
                                .addKeyValue("messageId", msgId)
                                .addKeyValue("content", new String(msg)).log();
                        responseListBuilder.addResponse(Response.responseOK(msgId));
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                        if (producerProperties.isDropInvalidMessages() && isSchemaSerializationFailure(cause != null ? cause : ex)) {
                            log.atWarn().setMessage("Dropping message due to schema/serialization error.")
                                    .addKeyValue("messageId", msgId)
                                    .addKeyValue("dropInvalidMessages", true)
                                    .addKeyValue("error", cause != null ? cause.getMessage() : ex.getMessage()).log();
                            responseListBuilder.addResponse(Response.responseOK(msgId));
                        } else if (isSchemaSerializationFailure(cause != null ? cause : ex)) {
                            log.atWarn().setMessage("Message failed schema validation.")
                                    .addKeyValue("messageId", msgId)
                                    .addKeyValue("error", cause != null ? cause.getMessage() : ex.getMessage()).log();
                            responseListBuilder.addResponse(Response.responseFailure(msgId, cause != null ? cause.getMessage() : ex.getMessage()));
                        } else {
                            log.atError().setMessage("Error processing message.")
                                    .addKeyValue("messageId", msgId).setCause(ex).log();
                            responseListBuilder.addResponse(Response.responseFailure(msgId, ex.getMessage()));
                        }
                        return null;
                    });

            futures.add(future);
        }

        // Wait for all sends to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return responseListBuilder.build();
    }

    /**
     * True if the failure is due to schema/serialization (e.g. invalid Avro, EOF).
     * Pulsar wraps these in SchemaSerializationException (e.g. around EOFException).
     */
    private static boolean isSchemaSerializationFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SchemaSerializationException) {
                return true;
            }
        }
        return false;
    }

    public void cleanup() {
        try {
            if (producer != null) {
                // Push buffered records before closing so shutdown does not drop already accepted messages.
                producer.flush();
                log.atInfo().setMessage("Producer flushed.").log();
            }
        } catch (PulsarClientException e) {
            log.atWarn().setMessage("Error while flushing producer during shutdown.").setCause(e).log();
        }

        try {
            if (producer != null) {
                producer.close();
                log.atInfo().setMessage("Producer closed.").log();
            }
            if (pulsarClient != null) {
                pulsarClient.close();
                log.atInfo().setMessage("PulsarClient closed.").log();
            }
        } catch (PulsarClientException e) {
            log.atError().setMessage("Error while closing PulsarClient or Producer.").setCause(e).log();
        }
    }
}
