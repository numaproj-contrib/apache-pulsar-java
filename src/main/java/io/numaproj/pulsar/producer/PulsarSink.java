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
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.generic.GenericDatumWriter;
import java.io.ByteArrayOutputStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.File;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import org.apache.avro.generic.GenericDatumReader;
import java.io.ByteArrayInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.producer", name = "enabled", havingValue = "true")
public class PulsarSink extends Sinker {

    @Autowired
    private Producer<byte[]> producer;

    @Autowired
    private PulsarClient pulsarClient;

    private Server server;
    private Schema schema;

    @PostConstruct // starts server automatically when the spring context initializes
    public void startServer() throws Exception {
        // Load Pulsar schema definition file and extract Avro schema
        InputStream inputStream = new ClassPathResource("static/schema.avsc").getInputStream();
        String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(fileContent);
        String avroSchemaString = root.get("schema").asText();
        schema = new Schema.Parser().parse(avroSchemaString);
        log.info("Successfully loaded Avro schema: {}", schema.toString());

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
            // null means the iterator is closed, so we break
            if (datum == null) {
                break;
            }

            final String msgId = datum.getId();

            try {
                log.debug("Processing message ID: {}, content length: {}", msgId, datum.getValue().length);

                // Log the incoming JSON for debugging
                String jsonContent = new String(datum.getValue(), StandardCharsets.UTF_8);
                log.info("Incoming JSON content: {}", jsonContent);

                // Parse the incoming JSON to properly format it for Avro union type
                ObjectMapper mapper = new ObjectMapper();
                JsonNode originalJson = mapper.readTree(datum.getValue());

                // Create a new JSON structure that follows Avro union type format
                ObjectNode formattedJson = mapper.createObjectNode();

                // Handle the Data field as a union type
                JsonNode dataNode = originalJson.get("Data");
                if (dataNode == null || dataNode.isNull()) {
                    formattedJson.putNull("Data");
                } else {
                    // For union types in Avro, we need to specify which type we're using
                    ObjectNode dataUnion = mapper.createObjectNode();
                    ObjectNode dataRecord = mapper.createObjectNode();

                    // Copy the value field
                    if (dataNode.has("value")) {
                        dataRecord.put("value", dataNode.get("value").asLong());
                    }

                    // Add the padding field (null by default as per schema)
                    dataRecord.putNull("padding");

                    // Add the DataRecord to the union
                    dataUnion.set("DataRecord", dataRecord);
                    formattedJson.set("Data", dataUnion);
                }

                // Copy the Createdts field
                if (originalJson.has("Createdts")) {
                    formattedJson.put("Createdts", originalJson.get("Createdts").asLong());
                }

                log.info("Formatted JSON for Avro: {}", formattedJson.toString());

                // Convert the formatted JSON to Avro
                byte[] formattedJsonBytes = formattedJson.toString().getBytes(StandardCharsets.UTF_8);
                Decoder jsonDecoder = DecoderFactory.get().jsonDecoder(schema,
                        new ByteArrayInputStream(formattedJsonBytes));
                DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
                GenericRecord avroRecord = reader.read(null, jsonDecoder);

                // Serialize the Avro record
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
                Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
                writer.write(avroRecord, encoder);
                encoder.flush();
                out.close();

                final byte[] avroBytes = out.toByteArray();

                // Send the serialized Avro message
                CompletableFuture<Void> future = producer.sendAsync(avroBytes)
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