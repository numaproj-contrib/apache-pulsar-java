package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.OutputObserver;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Server;
import io.numaproj.numaflow.sourcer.Sourcer;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;
import lombok.extern.slf4j.Slf4j;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.common.policies.data.TopicStats;
import org.apache.pulsar.common.policies.data.SubscriptionStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.BinaryDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.json.JSONObject;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    // Map tracking received messages (keyed by Pulsar message ID string)
    private final Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = new HashMap<>();

    private Server server;

    @Autowired
    private PulsarConsumerManager pulsarConsumerManager;

    @Autowired
    private PulsarAdmin pulsarAdmin;

    @Autowired
    PulsarConsumerProperties pulsarConsumerProperties;

    private Schema avroSchema;

    @PostConstruct
    public void startServer() throws Exception {
        // Load the Avro schema
        try {
            ObjectMapper mapper = new ObjectMapper();
            String schemaStr = new String(new ClassPathResource("schema.avsc").getInputStream().readAllBytes());
            avroSchema = new Schema.Parser().parse(schemaStr);
            log.info("Loaded AVRO schema for consumer: {}", avroSchema.toString(true));
        } catch (IOException e) {
            log.error("Failed to parse AVRO schema", e);
            throw e;
        }

        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    private GenericRecord deserializeAvroRecord(byte[] bytes) throws IOException {
        DatumReader<GenericRecord> reader = new GenericDatumReader<>(avroSchema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
        return reader.read(null, decoder);
    }

    private void logAvroRecord(GenericRecord record) {
        log.info("  Createdts: {}", record.get("Createdts"));
        if (record.get("Data") != null) {
            GenericRecord dataRecord = (GenericRecord) record.get("Data");
            log.info("  Data:");
            log.info("    value: {}", dataRecord.get("value"));
            log.info("    padding: {}", dataRecord.get("padding"));
        } else {
            log.info("  Data: null");
        }
    }

    private byte[] convertAvroRecordToJson(GenericRecord record) {
        JSONObject json = convertAvroFieldToJson(record);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private JSONObject convertAvroFieldToJson(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof GenericRecord) {
            GenericRecord record = (GenericRecord) value;
            JSONObject json = new JSONObject();
            record.getSchema().getFields().forEach(field -> {
                String fieldName = field.name();
                Object fieldValue = record.get(fieldName);
                if (fieldValue instanceof GenericRecord) {
                    json.put(fieldName, convertAvroFieldToJson(fieldValue));
                } else if (fieldValue instanceof List) {
                    json.put(fieldName, convertAvroListToJson((List<?>) fieldValue));
                } else if (fieldValue instanceof Map) {
                    json.put(fieldName, convertAvroMapToJson((Map<?, ?>) fieldValue));
                } else {
                    json.put(fieldName, fieldValue);
                }
            });
            return json;
        }

        return new JSONObject().put("value", value);
    }

    private JSONObject convertAvroMapToJson(Map<?, ?> map) {
        JSONObject json = new JSONObject();
        map.forEach((key, value) -> {
            if (value instanceof GenericRecord) {
                json.put(key.toString(), convertAvroFieldToJson(value));
            } else if (value instanceof List) {
                json.put(key.toString(), convertAvroListToJson((List<?>) value));
            } else if (value instanceof Map) {
                json.put(key.toString(), convertAvroMapToJson((Map<?, ?>) value));
            } else {
                json.put(key.toString(), value);
            }
        });
        return json;
    }

    private org.json.JSONArray convertAvroListToJson(List<?> list) {
        org.json.JSONArray jsonArray = new org.json.JSONArray();
        list.forEach(item -> {
            if (item instanceof GenericRecord) {
                jsonArray.put(convertAvroFieldToJson(item));
            } else if (item instanceof List) {
                jsonArray.put(convertAvroListToJson((List<?>) item));
            } else if (item instanceof Map) {
                jsonArray.put(convertAvroMapToJson((Map<?, ?>) item));
            } else {
                jsonArray.put(item);
            }
        });
        return jsonArray;
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        // If there are messages not acknowledged, return
        if (!messagesToAck.isEmpty()) {
            log.trace("messagesToAck not empty: {}", messagesToAck);
            return;
        }

        Consumer<byte[]> consumer = null;

        try {
            // Obtain a consumer with the desired settings.
            consumer = pulsarConsumerManager.getOrCreateConsumer(request.getCount(), request.getTimeout().toMillis());

            Messages<byte[]> batchMessages = consumer.batchReceive();

            if (batchMessages == null || batchMessages.size() == 0) {
                log.trace("Received 0 messages, return early.");
                return;
            }

            for (org.apache.pulsar.client.api.Message<byte[]> pMsg : batchMessages) {
                String msgId = pMsg.getMessageId().toString();
                byte[] rawBytes = pMsg.getValue();

                try {
                    GenericRecord deserializedRecord = deserializeAvroRecord(rawBytes);
                    log.info("Consumed Pulsar message [id: {}]:", msgId);
                    logAvroRecord(deserializedRecord);

                    byte[] jsonBytes = convertAvroRecordToJson(deserializedRecord);
                    log.debug("Converted to JSON: {}", new String(jsonBytes, StandardCharsets.UTF_8));

                    byte[] offsetBytes = msgId.getBytes(StandardCharsets.UTF_8);
                    Offset offset = new Offset(offsetBytes);

                    // Send the JSON bytes instead of raw Avro bytes
                    Message message = new Message(jsonBytes, offset, Instant.now());
                    observer.send(message);

                    messagesToAck.put(msgId, pMsg);
                } catch (IOException e) {
                    log.error("Failed to process Avro message [id: {}]: {}", msgId, e.getMessage());
                    continue;
                }
            }
        } catch (PulsarClientException e) {
            log.error("Failed to get consumer or receive messages from Pulsar", e);
            throw new RuntimeException("Failed to get consumer or receive messages from Pulsar", e);
        }
    }

    @Override
    public void ack(AckRequest request) {
        // Convert offsets to message ID strings for comparison
        Map<String, Offset> requestOffsetMap = new HashMap<>(); // key: msgId, value: offset object
        request.getOffsets().forEach(offset -> {
            // Offset value is a byte array so convert byte arr to string
            String messageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
            requestOffsetMap.put(messageIdKey, offset);
        });

        // Verify that the keys in messagesToAck match the message IDs from the request
        if (!messagesToAck.keySet().equals(requestOffsetMap.keySet())) {
            log.error("Mismatch in acknowledgment: internal pending IDs {} do not match requested ack IDs {}",
                    messagesToAck.keySet(), requestOffsetMap.keySet());
            // Return early without processing the ack to prevent any inconsistent state
            return;
        }

        // If the check passed, process each ack request
        for (Map.Entry<String, Offset> entry : requestOffsetMap.entrySet()) {
            String messageIdKey = entry.getKey();
            org.apache.pulsar.client.api.Message<byte[]> pMsg = messagesToAck.get(messageIdKey);
            if (pMsg != null) {
                try {
                    Consumer<byte[]> consumer = pulsarConsumerManager.getOrCreateConsumer(0, 0);
                    consumer.acknowledge(pMsg);
                    log.info("Acknowledged Pulsar message with ID: {} and payload: {}",
                            messageIdKey, new String(pMsg.getValue(), StandardCharsets.UTF_8));
                } catch (PulsarClientException e) {
                    log.error("Failed to acknowledge Pulsar message", e);
                }
                messagesToAck.remove(messageIdKey);
            } else {
                log.warn("Requested message ID {} not found in the pending acks", messageIdKey);
            }
        }
    }

    @Override
    public long getPending() {
        try {
            // TODO - If changing to support multiple topics, we need to update this
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            String topicName = topicNames.iterator().next(); // Assumes there is only one topic name in the set
            String subscriptionName = (String) pulsarConsumerProperties.getConsumerConfig().get("subscriptionName");

            int partitionCount = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName).partitions;
            if (partitionCount > 0) {
                // Topic is partitioned, so we should use partitionedStats
                var partitionedStats = pulsarAdmin.topics().getPartitionedStats(topicName, false);
                // If the subscription exists at the partitioned level, get its aggregated
                // backlog
                if (partitionedStats.getSubscriptions().containsKey(subscriptionName)) {
                    long backlog = partitionedStats.getSubscriptions().get(subscriptionName).getMsgBacklog();
                    log.info("Number of messages in the backlog (partitioned) for subscription {}: {}",
                            subscriptionName, backlog);
                    return backlog;
                } else {
                    // If subscription not found at top-level stats, sum the backlog across each
                    // partition
                    long totalBacklog = partitionedStats.getPartitions().values().stream()
                            .mapToLong(ts -> {
                                var subStats = ts.getSubscriptions().get(subscriptionName);
                                return (subStats != null) ? subStats.getMsgBacklog() : 0;
                            })
                            .sum();
                    log.info("Number of messages in the backlog (partitioned sum) for subscription {}: {}",
                            subscriptionName,
                            totalBacklog);
                    return totalBacklog;
                }
            } else {
                // Non-partitioned topic–safe to call getStats directly
                TopicStats topicStats = pulsarAdmin.topics().getStats(topicName);
                SubscriptionStats subscriptionStats = topicStats.getSubscriptions().get(subscriptionName);
                log.info("Number of messages in the backlog: {}", subscriptionStats.getMsgBacklog());
                return subscriptionStats.getMsgBacklog();
            }

        } catch (PulsarAdminException e) {
            log.error("Error while fetching admin stats for pending messages", e);
            // Return a negative value to indicate no pending information
            return -1;
        }
    }

    @Override
    public List<Integer> getPartitions() {
        try {
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            // Assume single topic in the set
            String topicName = topicNames.iterator().next();

            int numPartitions = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName).partitions;
            log.info("Number of partitions reported by metadata for topic {}: {}", topicName, numPartitions);

            // If it's not partitioned, Pulsar returns 0 partitions
            if (numPartitions < 1) {
                log.warn("Topic {} is not reported as partitioned", topicName);
                return List.of(0);
            }

            // Otherwise, build the partition indexes from 0..(numPartitions-1)
            List<Integer> partitionIndexes = new ArrayList<>();
            for (int i = 0; i < numPartitions; i++) {
                partitionIndexes.add(i);
            }
            return partitionIndexes;

        } catch (Exception e) {
            log.error("Error while retrieving partition information. Falling back to default partitions.", e);
            return defaultPartitions();
        }
    }

}