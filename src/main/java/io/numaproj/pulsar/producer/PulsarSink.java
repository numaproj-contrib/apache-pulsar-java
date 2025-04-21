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
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
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
        // Load schema once during initialization
        try {
            InputStream inputStream = new ClassPathResource("static/just-schema.json").getInputStream();
            schema = new Schema.Parser().parse(inputStream);
            log.info("Successfully loaded Avro schema: {}", schema.toString());
        } catch (IOException e) {
            log.error("Failed to parse Avro schema from file. Server will not start.", e);
            throw new RuntimeException("Failed to load Avro schema", e);
        }

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

                // Create a new GenericRecord directly based on the schema
                GenericRecord dataRecord = new GenericData.Record(schema.getField("Data").schema());
                dataRecord.put("value", System.currentTimeMillis());
                dataRecord.put("padding", null);

                // Create a GenericRecord for the Avro schema
                GenericRecord avroRecord = new GenericData.Record(schema);
                avroRecord.put("Data", dataRecord);
                avroRecord.put("Createdts", System.currentTimeMillis());

                // Serialize the Avro record
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DatumWriter<GenericRecord> writer = new SpecificDatumWriter<>(schema);
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