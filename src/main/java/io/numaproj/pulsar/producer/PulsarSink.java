package io.numaproj.pulsar.producer;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.Response;
import io.numaproj.numaflow.sinker.ResponseList;
import io.numaproj.numaflow.sinker.Server;
import io.numaproj.numaflow.sinker.Sinker;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.numaproj.pulsar.model.numagen;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<numagen> producer;

    @Autowired
    private PulsarClient pulsarClient;

    private Server server;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
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
            if (datum == null) {
                break;
            }

            final String msgId = datum.getId();

            try {
                log.debug("Processing message ID: {}, content length: {}", msgId, datum.getValue().length);

                // Parse the incoming JSON to our Avro-generated class
                String jsonContent = new String(datum.getValue(), StandardCharsets.UTF_8);
                log.info("Incoming JSON content: {}", jsonContent);

                numagen message = objectMapper.readValue(jsonContent, numagen.class);

                // Log the message that will be sent
                log.info("Sending message - createdts: {}, data.value: {}, data.padding: {}",
                        message.getCreatedts(),
                        message.getData() != null ? message.getData().getValue() : "null",
                        message.getData() != null ? message.getData().getPadding() : "null");

                // Send the message
                CompletableFuture<Void> future = producer.sendAsync(message)
                        .thenAccept(messageId -> {
                            log.info("Processed message ID: {}, data sent successfully", msgId);
                            responseListBuilder.addResponse(Response.responseOK(msgId));
                        })
                        .exceptionally(ex -> {
                            log.error("Error processing message ID {}: {}", msgId, ex.getMessage(), ex);
                            responseListBuilder.addResponse(Response.responseFailure(msgId, ex.getMessage()));
                            return null;
                        });

                futures.add(future);
            } catch (Exception e) {
                log.error("Exception during message processing for ID {}: {}", msgId, e.getMessage(), e);
                responseListBuilder.addResponse(Response.responseFailure(msgId, e.getMessage()));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return responseListBuilder.build();
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