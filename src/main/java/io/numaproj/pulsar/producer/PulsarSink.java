package io.numaproj.pulsar.producer;

import io.numaproj.numaflow.sinker.Datum;
import io.numaproj.numaflow.sinker.DatumIterator;
import io.numaproj.numaflow.sinker.Response;
import io.numaproj.numaflow.sinker.ResponseList;
import io.numaproj.numaflow.sinker.Server;
import io.numaproj.numaflow.sinker.Sinker;
import lombok.extern.slf4j.Slf4j;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<GenericRecord> producer;

    @Autowired
    private PulsarClient pulsarClient;

    private Server server;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Schema avroSchema;

    @PostConstruct
    public void startServer() throws Exception {
        // Load the Avro schema
        log.info("Attempting to load schema.avsc from classpath");
        try (InputStream schemaInputStream = getClass().getClassLoader().getResourceAsStream("schema.avsc")) {
            if (schemaInputStream == null) {
                log.error("Failed to find schema.avsc in classpath");
                throw new IOException("Schema file not found");
            }
            log.info("Successfully loaded schema.avsc from classpath");
            JsonNode schemaJson = objectMapper.readTree(schemaInputStream);
            String schemaStr = schemaJson.get("schema").asText();
            avroSchema = new Schema.Parser().parse(schemaStr);
            log.info("Successfully parsed Avro schema: {}", avroSchema.toString(true));
        } catch (Exception e) {
            log.error("Error loading or parsing schema: {}", e.getMessage(), e);
            throw e;
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
            if (datum == null) {
                break;
            }

            final String msgId = datum.getId();

            try {
                log.debug("Processing message ID: {}, content length: {}", msgId, datum.getValue().length);

                // Parse the incoming JSON
                String jsonContent = new String(datum.getValue(), StandardCharsets.UTF_8);
                log.info("Incoming JSON content: {}", jsonContent);
                JsonNode jsonNode = objectMapper.readTree(jsonContent);

                // Create Avro GenericRecord
                GenericRecord record = new GenericData.Record(avroSchema);
                record.put("Createdts", jsonNode.get("Createdts").asLong());

                if (jsonNode.has("Data") && !jsonNode.get("Data").isNull()) {
                    GenericRecord dataRecord = new GenericData.Record(
                            avroSchema.getField("Data").schema().getTypes().get(1));
                    dataRecord.put("value", jsonNode.get("Data").get("value").asLong());
                    if (jsonNode.get("Data").has("padding")) {
                        dataRecord.put("padding", jsonNode.get("Data").get("padding").asText());
                    }
                    record.put("Data", dataRecord);
                } else {
                    record.put("Data", null);
                }

                // Log the message that will be sent
                log.info("Sending message: {}", record);

                // Send the message
                CompletableFuture<Void> future = producer.sendAsync(record)
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