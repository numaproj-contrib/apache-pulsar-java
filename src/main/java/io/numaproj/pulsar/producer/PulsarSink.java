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
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<GenericRecord> producer;

    @Autowired
    private PulsarClient pulsarClient;

    private Server server;
    private Schema<GenericRecord> schema;

    @PostConstruct
    public void startServer() throws Exception {
        // Load Pulsar schema definition file
        InputStream inputStream = new ClassPathResource("static/schema.avsc").getInputStream();
        String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(fileContent);
        String avroSchemaString = root.get("schema").asText();

        // Create Schema for Avro
        schema = Schema.AUTO_CONSUME();
        log.info("Successfully loaded Avro schema: {}", avroSchemaString);

        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public ResponseList processMessages(DatumIterator datumIterator) {
        ResponseList.ResponseListBuilder responseListBuilder = ResponseList.newBuilder();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (schema == null) {
            log.error("Avro schema is not initialized");
            return responseListBuilder.build();
        }

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

                ObjectMapper mapper = new ObjectMapper();
                JsonNode originalJson = mapper.readTree(datum.getValue());

                // Create a new JSON structure that follows Avro union type format
                ObjectNode formattedJson = mapper.createObjectNode();

                // Handle the Data field as a union type
                JsonNode dataNode = originalJson.get("Data");
                if (dataNode == null || dataNode.isNull()) {
                    formattedJson.putNull("Data");
                } else {
                    ObjectNode dataUnion = mapper.createObjectNode();
                    ObjectNode dataRecord = mapper.createObjectNode();

                    if (dataNode.has("value")) {
                        dataRecord.put("value", dataNode.get("value").asLong());
                    }
                    dataRecord.putNull("padding");
                    dataUnion.set("DataRecord", dataRecord);
                    formattedJson.set("Data", dataUnion);
                }

                if (originalJson.has("Createdts")) {
                    formattedJson.put("Createdts", originalJson.get("Createdts").asLong());
                }

                log.info("Formatted JSON for Avro: {}", formattedJson.toString());

                // Convert JSON to GenericRecord using Pulsar's Schema
                GenericRecord record = schema.decode(formattedJson.toString().getBytes(StandardCharsets.UTF_8));

                // Send the record using Pulsar's native Avro support
                CompletableFuture<Void> future = producer.sendAsync(record)
                        .thenAccept(messageId -> {
                            log.info("Processed message ID: {}, Avro data sent successfully", msgId);
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