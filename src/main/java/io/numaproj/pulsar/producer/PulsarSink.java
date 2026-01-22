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
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.BinaryEncoder;
import io.numaproj.pulsar.config.producer.PulsarProducerProperties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

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

    @PostConstruct
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    private byte[] validateAndSerializeMessage(byte[] jsonBytes) throws IOException {
        // If no schema validation is required, return the raw bytes
        if (!producerProperties.getAvroSchema().isPresent()) {
            return jsonBytes;
        }

        Schema avroSchema = producerProperties.getAvroSchema().get();

        // Parse JSON and create Avro record
        String jsonStr = new String(jsonBytes);
        JSONObject json = new JSONObject(jsonStr);
        GenericRecord record = new GenericData.Record(avroSchema);

        // Set fields from JSON to record
        avroSchema.getFields().forEach(field -> {
            String fieldName = field.name();
            if (json.has(fieldName)) {
                Object value = json.get(fieldName);
                if (value instanceof JSONObject) {
                    // Handle nested JSON object by creating a nested GenericRecord
                    JSONObject nestedJson = (JSONObject) value;
                    Schema fieldSchema = field.schema();
                    // If it's a union type, find the record type
                    if (fieldSchema.getType() == Schema.Type.UNION) {
                        for (Schema s : fieldSchema.getTypes()) {
                            if (s.getType() == Schema.Type.RECORD) {
                                fieldSchema = s;
                                break;
                            }
                        }
                    }
                    GenericRecord nestedRecord = new GenericData.Record(fieldSchema);
                    fieldSchema.getFields().forEach(nestedField -> {
                        String nestedFieldName = nestedField.name();
                        if (nestedJson.has(nestedFieldName)) {
                            nestedRecord.put(nestedFieldName, nestedJson.get(nestedFieldName));
                        }
                    });
                    record.put(fieldName, nestedRecord);
                } else {
                    record.put(fieldName, value);
                }
            }
        });

        // Let Avro's built-in validation handle all the type checking and constraints
        if (!GenericData.get().validate(avroSchema, record)) {
            throw new IOException("Message failed Avro schema validation");
        }

        // If validation passes, serialize to Avro binary format
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(avroSchema);
        writer.write(record, encoder);
        encoder.flush();
        return outputStream.toByteArray();
    }

    @Override
    public ResponseList processMessages(DatumIterator datumIterator) {
        List<Response> responses = new ArrayList<>();
        List<CompletableFuture<Response>> futures = new ArrayList<>();

        try {
            while (true) {
                Datum datum = datumIterator.next();
                if (datum == null) {
                    break;
                }
                try {
                    log.info("Preparing to send message: {}", new String(datum.getValue()));
                    byte[] messageBytes = validateAndSerializeMessage(datum.getValue());

                    CompletableFuture<Response> future = producer.sendAsync(messageBytes)
                            .thenApply(msgId -> {
                                log.info("Successfully sent message with ID: {}", msgId);
                                return Response.responseOK(datum.getId());
                            })
                            .exceptionally(throwable -> {
                                log.error("Failed to send message", throwable);
                                return Response.responseFailure(datum.getId(), throwable.getMessage());
                            });

                    futures.add(future);
                } catch (Exception e) {
                    log.error("Error processing message", e);
                    responses.add(Response.responseFailure(datum.getId(), e.getMessage()));
                }
            }
        } catch (InterruptedException e) {
            log.error("Iterator was interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Wait for all async operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect all responses
        futures.forEach(future -> responses.add(future.join()));

        ResponseList.ResponseListBuilder builder = ResponseList.newBuilder();
        responses.forEach(builder::addResponse);
        return builder.build();
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