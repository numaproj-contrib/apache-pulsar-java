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

    // Map tracking received messages (keyed by Pulsar message ID string)
    private final Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = new HashMap<>();

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

            // Process each message in the batch.
            for (org.apache.pulsar.client.api.Message<byte[]> pMsg : batchMessages) {
                String msgId = pMsg.getMessageId().toString();
                log.info("Consumed Pulsar message [id: {}]: {}", pMsg.getMessageId(),
                        new String(pMsg.getValue(), StandardCharsets.UTF_8));

                byte[] offsetBytes = msgId.getBytes(StandardCharsets.UTF_8);
                Offset offset = new Offset(offsetBytes);

                Message message = new Message(pMsg.getValue(), offset, Instant.now());
                observer.send(message);

                messagesToAck.put(msgId, pMsg);
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
            // If changing to support multiple topics, we need to update this
            Set<String> topicNames = (Set<String>) pulsarConsumerProperties.getConsumerConfig().get("topicNames");
            String topicName = (String) topicNames.iterator().next(); // Assumes there is only one topic name in the set
            String subscriptionName = (String) pulsarConsumerProperties.getConsumerConfig().get("subscriptionName");

            int partitionCount = pulsarAdmin.topics().getPartitionedTopicMetadata(topicName).partitions;
            if (partitionCount > 0) {
                // Topic is partitioned, so we should use partitionedStats
                var partitionedStats = pulsarAdmin.topics().getPartitionedStats(topicName, false);
                // If the subscription exists at the partitioned level, get its aggregated
                // backlog
                if (partitionedStats.getSubscriptions().containsKey(subscriptionName)) {
                    long backlog = partitionedStats.getSubscriptions().get(subscriptionName).getMsgBacklog();
                    log.info("Number of messages in the backlog (partitioned) for {}: {}", subscriptionName, backlog);
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
                    log.info("Number of messages in the backlog (partitioned sum) for {}: {}", subscriptionName,
                            totalBacklog);
                    return totalBacklog;
                }
            } else {
                // Non-partitioned topic–safe to call getStats directly
                TopicStats topicStats = pulsarAdmin.topics().getStats(topicName);
                SubscriptionStats subscriptionStats = topicStats.getSubscriptions().get(subscriptionName);
                if (subscriptionStats == null) {
                    log.warn("Subscription {} not found in topic stats for non-partitioned topic {}.", subscriptionName,
                            topicName);
                    return 0;
                }
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
