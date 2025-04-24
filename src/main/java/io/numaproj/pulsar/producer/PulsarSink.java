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
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import org.json.JSONObject;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.generic.GenericDatumReader;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

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
            // Read the schema directly since it's now in the proper format
            String schemaStr = new String(new ClassPathResource("schema.avsc").getInputStream().readAllBytes());
            avroSchema = new Schema.Parser().parse(schemaStr);
            log.info("Loaded AVRO schema: {}", avroSchema.toString(true));
        } catch (IOException e) {
            log.error("Failed to parse AVRO schema", e);
            throw e;
        }

        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    private GenericRecord createAvroRecord(JSONObject json, Schema schema) {
        GenericRecord record = new GenericData.Record(schema);

        for (Schema.Field field : schema.getFields()) {
            String fieldName = field.name();
            Schema fieldSchema = field.schema();

            // Handle null values
            if (!json.has(fieldName) || json.isNull(fieldName)) {
                if (fieldSchema.getType() == Schema.Type.UNION &&
                        fieldSchema.getTypes().stream().anyMatch(s -> s.getType() == Schema.Type.NULL)) {
                    record.put(fieldName, null);
                } else if (field.hasDefaultValue()) {
                    record.put(fieldName, field.defaultVal());
                } else {
                    throw new IllegalArgumentException("Required field " + fieldName + " missing from JSON");
                }
                continue;
            }

            // If it's a union type, find the actual schema
            if (fieldSchema.getType() == Schema.Type.UNION) {
                for (Schema s : fieldSchema.getTypes()) {
                    if (s.getType() != Schema.Type.NULL) {
                        fieldSchema = s;
                        break;
                    }
                }
            }

            // Handle different types of fields
            switch (fieldSchema.getType()) {
                case RECORD:
                    JSONObject nestedJson = json.getJSONObject(fieldName);
                    GenericRecord nestedRecord = createAvroRecord(nestedJson, fieldSchema);
                    record.put(fieldName, nestedRecord);
                    break;
                case STRING:
                    record.put(fieldName, json.getString(fieldName));
                    break;
                case LONG:
                    record.put(fieldName, json.getLong(fieldName));
                    break;
                case INT:
                    record.put(fieldName, json.getInt(fieldName));
                    break;
                case BOOLEAN:
                    record.put(fieldName, json.getBoolean(fieldName));
                    break;
                case FLOAT:
                    record.put(fieldName, (float) json.getDouble(fieldName));
                    break;
                case DOUBLE:
                    record.put(fieldName, json.getDouble(fieldName));
                    break;
                case ARRAY:
                    // Handle array type if needed
                    break;
                case MAP:
                    // Handle map type if needed
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported field type: " + fieldSchema.getType() + " for field " + fieldName);
            }
        }

        return record;
    }

    private void logAvroRecord(GenericRecord record) {
        log.info("Record content:");
        logAvroRecordField(record, "  ");
    }

    private void logAvroRecordField(GenericRecord record, String indent) {
        for (Schema.Field field : record.getSchema().getFields()) {
            String fieldName = field.name();
            Object value = record.get(fieldName);

            if (value == null) {
                log.info("{}{}: null", indent, fieldName);
                continue;
            }

            Schema fieldSchema = field.schema();
            // If it's a union type, find the actual schema
            if (fieldSchema.getType() == Schema.Type.UNION) {
                for (Schema s : fieldSchema.getTypes()) {
                    if (s.getType() != Schema.Type.NULL) {
                        fieldSchema = s;
                        break;
                    }
                }
            }

            switch (fieldSchema.getType()) {
                case RECORD:
                    if (value instanceof GenericRecord) {
                        log.info("{}{}: ", indent, fieldName);
                        logAvroRecordField((GenericRecord) value, indent + "  ");
                    }
                    break;
                case ARRAY:
                    // Handle array type if needed
                    break;
                case MAP:
                    // Handle map type if needed
                    break;
                default:
                    log.info("{}{}: {}", indent, fieldName, value);
                    break;
            }
        }
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

            final String id = datum.getId();
            try {
                String jsonString = new String(datum.getValue());
                JSONObject jsonObject = new JSONObject(jsonString);
                log.info("Processing message ID: {} with input JSON: {}", id, jsonString);

                // Create Avro record using generic method
                GenericRecord avroRecord = createAvroRecord(jsonObject, avroSchema);

                // Log the pre-serialized Avro record in a readable format
                log.info("Pre-serialization - Message ID {}: Avro Record Content:", id);
                logAvroRecord(avroRecord);

                // Serialize to Avro binary format
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(avroSchema);
                Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
                writer.write(avroRecord, encoder);
                encoder.flush();
                outputStream.close();

                byte[] serializedBytes = outputStream.toByteArray();
                log.info("Post-serialization - Message ID {}: Serialized to {} bytes", id, serializedBytes.length);
                log.info("Serialized bytes: {}", java.util.Arrays.toString(serializedBytes));

                // Deserialize and verify
                GenericRecord deserializedRecord = deserializeAvroRecord(serializedBytes);
                log.info("Post-deserialization - Message ID {}: Deserialized Record Content:", id);
                logAvroRecord(deserializedRecord);

                // Verify records match
                boolean recordsMatch = avroRecord.equals(deserializedRecord);
                log.info("Verification - Message ID {}: Original and deserialized records {} match",
                        id, recordsMatch ? "DO" : "DO NOT");

                // Send the serialized Avro message
                CompletableFuture<Void> future = producer.sendAsync(serializedBytes)
                        .thenAccept(messageId -> {
                            log.info("Successfully sent message ID: {} to Pulsar with messageId: {}", id, messageId);
                            responseListBuilder.addResponse(Response.responseOK(id));
                        })
                        .exceptionally(ex -> {
                            log.error("Error sending message ID {}: {}", id, ex.getMessage(), ex);
                            responseListBuilder.addResponse(Response.responseFailure(id, ex.getMessage()));
                            return null;
                        });

                futures.add(future);
            } catch (Exception e) {
                log.error("Error processing message ID {}: {}", id, e.getMessage(), e);
                responseListBuilder.addResponse(Response.responseFailure(id, e.getMessage()));
            }
        }

        // Wait for all sends to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return responseListBuilder.build();
    }

    private GenericRecord deserializeAvroRecord(byte[] bytes) throws IOException {
        DatumReader<GenericRecord> reader = new GenericDatumReader<>(avroSchema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
        return reader.read(null, decoder);
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