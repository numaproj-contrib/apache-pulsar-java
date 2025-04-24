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
import org.apache.avro.Schema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<byte[]> producer;

    @Autowired
    private PulsarClient pulsarClient;

    private Server server;
    private Schema avroSchema;

    @PostConstruct
    public void startServer() throws Exception {
        // Parse and log the AVRO schema
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schemaJson = mapper.readTree(new ClassPathResource("schema.avsc").getInputStream());
            String schemaStr = schemaJson.get("schema").asText();
            avroSchema = new Schema.Parser().parse(schemaStr);
            log.info("Loaded AVRO schema: {}", avroSchema.toString(true));
        } catch (IOException e) {
            log.error("Failed to parse AVRO schema", e);
        }

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
            final String id = datum.getId(); // Get message ID from datum
            // Won't wait for broker to confirm receipt of msg before continuing
            // sendSync returns CompletableFuture which will complete when broker ack
            CompletableFuture<Void> future = producer.sendAsync(msg)
                    .thenAccept(messageId -> {
                        log.info("Processed message ID: {}, Content: {}", id, new String(msg));
                        responseListBuilder.addResponse(Response.responseOK(id));
                    })
                    .exceptionally(ex -> {
                        log.error("Error processing message ID {}: {}", id, ex.getMessage(), ex);
                        responseListBuilder.addResponse(Response.responseFailure(id, ex.getMessage()));
                        return null;
                    });

            futures.add(future);
        }

        // Wait for all sends to complete
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