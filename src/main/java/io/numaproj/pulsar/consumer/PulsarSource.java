package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.OutputObserver;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Server;
import io.numaproj.numaflow.sourcer.Sourcer;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;
import io.numaproj.pulsar.producer.numagen;
import lombok.extern.slf4j.Slf4j;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.common.policies.data.TopicStats;
import org.apache.pulsar.common.policies.data.SubscriptionStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    // Map tracking received messages (keyed by Pulsar message ID string)
    private final Map<String, org.apache.pulsar.client.api.Message<GenericRecord>> messagesToAck = new HashMap<>();

    private Server server;

    @Autowired
    private PulsarConsumerManager pulsarConsumerManager;

    @Autowired
    private PulsarAdmin pulsarAdmin;

    @Autowired
    PulsarConsumerProperties pulsarConsumerProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        try {
            if (!messagesToAck.isEmpty()) {
                log.warn("Messages to ack is not empty. Size: {}. Returning early.", messagesToAck.size());
                return;
            }

            Consumer<GenericRecord> consumer = pulsarConsumerManager.getOrCreateConsumer(request.getCount(),
                    request.getTimeout().toMillis());

            Messages<GenericRecord> messages = consumer.batchReceive();
            if (messages == null) {
                log.debug("No messages received within timeout");
                return;
            }

            for (org.apache.pulsar.client.api.Message<GenericRecord> msg : messages) {
                String messageId = msg.getMessageId().toString();
                messagesToAck.put(messageId, msg);

                GenericRecord message = msg.getValue();

                // Convert GenericRecord to Map recursively
                Map<String, Object> messageData = new HashMap<>();
                message.getFields().forEach(field -> {
                    String fieldName = field.getName();
                    Object fieldValue = message.getField(field);

                    // Handle nested GenericRecord
                    if (fieldValue instanceof GenericRecord) {
                        Map<String, Object> nestedMap = new HashMap<>();
                        GenericRecord nestedRecord = (GenericRecord) fieldValue;
                        nestedRecord.getFields().forEach(nestedField -> {
                            String nestedName = nestedField.getName();
                            Object nestedValue = nestedRecord.getField(nestedField);
                            nestedMap.put(nestedName, nestedValue);
                        });
                        messageData.put(fieldName, nestedMap);
                    } else {
                        messageData.put(fieldName, fieldValue);
                    }
                });

                // Convert the map to JSON
                String jsonValue = objectMapper.writeValueAsString(messageData);
                log.info("Sending message to observer: {}", jsonValue);

                Message numaMessage = new Message(
                        jsonValue.getBytes(StandardCharsets.UTF_8),
                        new Offset(messageId.getBytes(StandardCharsets.UTF_8)),
                        Instant.ofEpochMilli(msg.getEventTime() > 0 ? msg.getEventTime() : msg.getPublishTime()));

                observer.send(numaMessage);
            }

        } catch (Exception e) {
            log.error("Error while reading messages", e);
        }
    }

    @Override
    public void ack(AckRequest request) {
        Map<String, Offset> requestOffsetMap = new HashMap<>();
        request.getOffsets().forEach(offset -> {
            String messageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
            requestOffsetMap.put(messageIdKey, offset);
        });

        if (!messagesToAck.keySet().equals(requestOffsetMap.keySet())) {
            log.error("Mismatch in acknowledgment: internal pending IDs {} do not match requested ack IDs {}",
                    messagesToAck.keySet(), requestOffsetMap.keySet());
            return;
        }

        for (Map.Entry<String, Offset> entry : requestOffsetMap.entrySet()) {
            String messageIdKey = entry.getKey();
            org.apache.pulsar.client.api.Message<GenericRecord> pMsg = messagesToAck.get(messageIdKey);
            if (pMsg != null) {
                try {
                    Consumer<GenericRecord> consumer = pulsarConsumerManager.getOrCreateConsumer(0, 0);
                    consumer.acknowledge(pMsg);
                    log.info("Acknowledged Pulsar message with ID: {}", messageIdKey);
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
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            String topicName = topicNames.iterator().next();
            String subscriptionName = (String) pulsarConsumerProperties.getConsumerConfig().get("subscriptionName");

            int partitionCount = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName).partitions;
            if (partitionCount > 0) {
                var partitionedStats = pulsarAdmin.topics().getPartitionedStats(topicName, false);
                if (partitionedStats.getSubscriptions().containsKey(subscriptionName)) {
                    long backlog = partitionedStats.getSubscriptions().get(subscriptionName).getMsgBacklog();
                    log.info("Number of messages in the backlog (partitioned) for subscription {}: {}",
                            subscriptionName, backlog);
                    return backlog;
                } else {
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
                TopicStats topicStats = pulsarAdmin.topics().getStats(topicName);
                SubscriptionStats subscriptionStats = topicStats.getSubscriptions().get(subscriptionName);
                log.info("Number of messages in the backlog: {}", subscriptionStats.getMsgBacklog());
                return subscriptionStats.getMsgBacklog();
            }

        } catch (PulsarAdminException e) {
            log.error("Error while fetching admin stats for pending messages", e);
            return -1;
        }
    }

    @Override
    public List<Integer> getPartitions() {
        try {
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            String topicName = topicNames.iterator().next();

            int numPartitions = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName).partitions;
            log.info("Number of partitions reported by metadata for topic {}: {}", topicName, numPartitions);

            if (numPartitions < 1) {
                log.warn("Topic {} is not reported as partitioned", topicName);
                return List.of(0);
            }

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
