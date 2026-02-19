package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.NackRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.OutputObserver;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Server;
import io.numaproj.numaflow.sourcer.Sourcer;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;
import lombok.extern.slf4j.Slf4j;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SchemaSerializationException;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.Field;
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

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    // Map tracking received messages (keyed by topicName + messageId for multi-topic support).
    // Value is Message<?> because we hold either Message<byte[]> or Message<GenericRecord> depending on
    // useAutoConsumeSchema. only use the Message interface from the map (getMessageId, getTopicName, etc.
    // for ack and buildHeaders), never the payload type, so a single map with Message<?> is appropriate;
    private final Map<String, org.apache.pulsar.client.api.Message<?>> messagesToAck = new HashMap<>();

    private Server server;

    @Autowired
    private PulsarConsumerManager pulsarConsumerManager;

    @Autowired
    private PulsarAdmin pulsarAdmin;

    @Autowired
    PulsarConsumerProperties pulsarConsumerProperties;

    @PostConstruct
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        if (!messagesToAck.isEmpty()) {
            log.trace("messagesToAck not empty: {}", messagesToAck);
            return;
        }

        try {
            if (pulsarConsumerProperties.isUseAutoConsumeSchema()) {
                readWithAutoConsume(request, observer);
            } else {
                readWithBytes(request, observer);
            }
        } catch (Exception e) {
            log.error("Failed to read from Pulsar", e);
            throw new RuntimeException(e);
        }
    }

    private void readWithBytes(ReadRequest request, OutputObserver observer) throws PulsarClientException {
        Consumer<byte[]> consumer = pulsarConsumerManager.getOrCreateBytesConsumer(request.getCount(), request.getTimeout().toMillis());
        Messages<byte[]> batchMessages = consumer.batchReceive();

        if (batchMessages == null || batchMessages.size() == 0) {
            log.trace("Received 0 messages, return early.");
            return;
        }

        for (org.apache.pulsar.client.api.Message<byte[]> pMsg : batchMessages) {

            // TODO : change to .debug or .trace to reduce log noise
            log.info("Consumed Pulsar message [topic: {}, id: {}]: {}", pMsg.getTopicName(), pMsg.getMessageId(),
                    new String(pMsg.getValue(), StandardCharsets.UTF_8));
            sendMessage(pMsg, pMsg.getValue(), observer);
        }
    }

    private void readWithAutoConsume(ReadRequest request, OutputObserver observer) throws PulsarClientException {
        Consumer<GenericRecord> consumer = pulsarConsumerManager.getOrCreateGenericRecordConsumer(request.getCount(), request.getTimeout().toMillis());
        Messages<GenericRecord> batchMessages = consumer.batchReceive();

        if (batchMessages == null || batchMessages.size() == 0) {
            log.trace("Received 0 messages, return early.");
            return;
        }

        for (org.apache.pulsar.client.api.Message<GenericRecord> pMsg : batchMessages) {

            try {
                GenericRecord record = pMsg.getValue(); 
                byte[] payloadBytes = pMsg.getData();

                String decoded = recordToLogString(record);
                // TODO : change to .debug or .trace to reduce log noise
                log.info("Consumed Pulsar message (AUTO_CONSUME) [topic: {}, id: {}]: {} bytes, decoded={}", pMsg.getTopicName(), pMsg.getMessageId(), payloadBytes.length, decoded);
                sendMessage(pMsg, payloadBytes, observer);
            } catch (Exception e) {
                if (!isSchemaValidationFailure(e)) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException("Schema validation failure", e);
            }
        }
    }

    /** Builds offset and headers, sends the message to the observer, and records it for ack. */
    private void sendMessage(org.apache.pulsar.client.api.Message<?> pMsg, byte[] payloadBytes, OutputObserver observer) {
        String topicMessageIdKey = pMsg.getTopicName() + pMsg.getMessageId().toString();
        byte[] offsetBytes = topicMessageIdKey.getBytes(StandardCharsets.UTF_8);
        Offset offset = new Offset(offsetBytes);
        Map<String, String> headers = buildHeaders(pMsg);
        observer.send(new Message(payloadBytes, offset, Instant.now(), headers));
        messagesToAck.put(topicMessageIdKey, pMsg);
    }

    /** True if the exception indicates message schema validation / deserialization failure (wrong schema, unsupported type, or decode I/O). */
    private static boolean isSchemaValidationFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SchemaSerializationException
                    || t instanceof IOException
                    || t instanceof UnsupportedOperationException) {
                return true;
            }
        }
        return false;
    }

    // TODO : remove this logging later to reduce log noise
    /** Builds a log-friendly string of the decoded record (field names and values). */
    private static String recordToLogString(GenericRecord record) {
        if (record == null) {
            return "null";
        }
        List<Field> fields = record.getFields();
        if (fields == null || fields.isEmpty()) {
            return record.getSchemaType() + ":{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(record.getSchemaType()).append(":{");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(", ");
            Field f = fields.get(i);
            String name = f.getName();
            Object value = record.getField(f);
            sb.append(name).append("=").append(value);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void ack(AckRequest request) {
        // Offsets are topicName + messageId (same as messagesToAck key).
        Map<String, Offset> requestOffsetMap = new HashMap<>();
        request.getOffsets().forEach(offset -> {
            String topicMessageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
            requestOffsetMap.put(topicMessageIdKey, offset);
        });

        // Verify that the keys in messagesToAck match the offsets from the request
        if (!messagesToAck.keySet().equals(requestOffsetMap.keySet())) {
            log.error("Mismatch in acknowledgment: internal pending IDs {} do not match requested ack IDs {}",
                    messagesToAck.keySet(), requestOffsetMap.keySet());
            // Return early without processing the ack to prevent any inconsistent state
            return;
        }

        // because request key values and messsages to ack key values already match, can directly iterate over the messagesToAck map values to get the message ids
        List<MessageId> messageIds = messagesToAck.values().stream()
                .map(org.apache.pulsar.client.api.Message::getMessageId)
                .toList();

        try {
            Consumer<?> consumer = pulsarConsumerManager.getConsumer();
            consumer.acknowledge(messageIds);
            log.info("Successfully acknowledged {} messages", messageIds.size());
        } catch (PulsarClientException e) {
            log.error("Failed to acknowledge Pulsar messages", e);
        }
        messagesToAck.clear();
    }

    /**
     * Builds headers from Pulsar message metadata. Works for both Message<?> (byte[] or GenericRecord).
     */
    private Map<String, String> buildHeaders(org.apache.pulsar.client.api.Message<?> pulsarMessage) {
        Map<String, String> headers = new HashMap<>();

        headers.put(NumaHeaderKeys.PULSAR_PRODUCER_NAME, pulsarMessage.getProducerName());
        headers.put(NumaHeaderKeys.PULSAR_MESSAGE_ID, pulsarMessage.getMessageId().toString());
        headers.put(NumaHeaderKeys.PULSAR_TOPIC_NAME, pulsarMessage.getTopicName());
        headers.put(NumaHeaderKeys.PULSAR_PUBLISH_TIME, String.valueOf(pulsarMessage.getPublishTime()));
        headers.put(NumaHeaderKeys.PULSAR_EVENT_TIME, String.valueOf(pulsarMessage.getEventTime()));
        headers.put(NumaHeaderKeys.PULSAR_REDELIVERY_COUNT, String.valueOf(pulsarMessage.getRedeliveryCount()));

        if (pulsarMessage.getProperties() != null && !pulsarMessage.getProperties().isEmpty()) {
            pulsarMessage.getProperties().forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.put(key, value);
                }
            });
        }

        log.trace("Message headers: {}", headers);
        return headers;
    }

    @Override
    public void nack(NackRequest request) {
        // TODO : implement nack logic
        throw new UnsupportedOperationException("Unimplemented method 'nack'");
    }

    @Override
    public long getPending() {
        try {
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            String subscriptionName = (String) pulsarConsumerProperties.getConsumerConfig().get("subscriptionName");

            long totalBacklog = 0;
            for (String topicName : topicNames) {
                totalBacklog += getBacklogForTopic(topicName, subscriptionName);
            }
            log.info("Total messages in backlog across {} topic(s) for subscription {}: {}",
                    topicNames.size(), subscriptionName, totalBacklog);
            return totalBacklog;
        } catch (PulsarAdminException e) {
            log.error("Error while fetching admin stats for pending messages", e);
            return -1;
        }
    }

    private long getBacklogForTopic(String topicName, String subscriptionName) throws PulsarAdminException {
        var metadata = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName);
        int partitionCount = (metadata != null) ? metadata.partitions : 0;
        // Topic is partitioned, so we should use partitionedStats
        if (partitionCount > 0) {
            var partitionedStats = pulsarAdmin.topics().getPartitionedStats(topicName, false);
            // If the subscription exists at the partitioned level, get its aggregated backlog
            if (partitionedStats.getSubscriptions().containsKey(subscriptionName)) {
                return partitionedStats.getSubscriptions().get(subscriptionName).getMsgBacklog();
            }
            // If subscription not found at top-level stats, sum the backlog across each partition
            return partitionedStats.getPartitions().values().stream()
                    .mapToLong(ts -> {
                        var subStats = ts.getSubscriptions().get(subscriptionName);
                        return (subStats != null) ? subStats.getMsgBacklog() : 0;
                    })
                    .sum();
        }
        // Non-partitioned topic - safe to call getStats directly
        TopicStats topicStats = pulsarAdmin.topics().getStats(topicName);
        SubscriptionStats subscriptionStats = topicStats.getSubscriptions().get(subscriptionName);
        return subscriptionStats != null ? subscriptionStats.getMsgBacklog() : 0;
    }

    /**
     * Returns partition indices for this source. Numaflow uses this list to decide which partitions
     * exist for watermark publishing and scaling (same contract as Kafka-style multi-partition sources).
     * We expose one integer per Pulsar partition across all configured topics as a flat list [0, 1, 2, ...].
     */
    @Override
    public List<Integer> getPartitions() {
        try {
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            List<Integer> partitionIndexes = new ArrayList<>();
            // Assign one integer per partition across all topics (e.g. topic A has 3 partitions -> 0,1,2; topic B has 2 -> 3,4).
            int globalIndex = 0;
            for (String topicName : topicNames) {
                var metadata = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName);
                int numPartitions = (metadata != null) ? metadata.partitions : 0;
                log.info("Number of partitions reported for topic {}: {}", topicName, numPartitions);
                if (numPartitions < 1) {
                    log.warn("Topic '{}' is non-partitioned (partitions={}). It will be treated as a single partition.", topicName, numPartitions);
                }
                int effectivePartitions = numPartitions < 1 ? 1 : numPartitions;
                for (int i = 0; i < effectivePartitions; i++) {
                    partitionIndexes.add(globalIndex++);
                }
            }
            if (partitionIndexes.isEmpty()) {
                partitionIndexes.add(0);
            }
            return partitionIndexes;
        } catch (Exception e) {
            log.error("Error while retrieving partition information. Falling back to default partitions.", e);
            return defaultPartitions();
        }
    }

}
