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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<byte[]> producer;

    @Autowired
    private PulsarClient pulsarClient;

    @Autowired
    private PulsarProducerProperties producerProperties;

    private Server server;

    @PostConstruct // starts server automatically when the spring context initializes
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
                        log.info("Processed message ID: {}, Content: {}", msgId, new String(msg));
                        responseListBuilder.addResponse(Response.responseOK(msgId));
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                        if (producerProperties.isDropInvalidMessages() && isSchemaSerializationFailure(cause != null ? cause : ex)) {
                            log.warn("Dropping message ID {} due to schema/serialization error (drop-invalid-messages=true): {}",
                                    msgId, cause != null ? cause.getMessage() : ex.getMessage());
                            responseListBuilder.addResponse(Response.responseOK(msgId));
                        } else if (isSchemaSerializationFailure(cause != null ? cause : ex)) {
                            log.warn("Message ID {} failed schema validation, messages produced do not align with topic schema: {}", msgId, cause != null ? cause.getMessage() : ex.getMessage());
                            responseListBuilder.addResponse(Response.responseFailure(msgId, cause != null ? cause.getMessage() : ex.getMessage()));
                        } else {
                            log.error("Error processing message ID {}: {}", msgId, ex.getMessage(), ex);
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

    @PreDestroy
    public void cleanup() {
        try {
            if (producer != null) {
                producer.close();
                log.info("Producer closed.");
            }
            if (pulsarClient != null) {
                pulsarClient.close();
                log.info("PulsarClient closed.");
            }
        } catch (PulsarClientException e) {
            log.error("Error while closing PulsarClient or Producer.", e);
        }
    }
}