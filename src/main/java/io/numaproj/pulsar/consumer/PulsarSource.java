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

    // Map tracking received messages (keyed by topicName + messageId for multi-topic support)
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
                String topicName = pMsg.getTopicName();
                String msgId = pMsg.getMessageId().toString();
                String topicMessageIdKey = topicName + msgId;
                
                // TODO : change to .debug or .trace to reduce log noise
                log.info("Consumed Pulsar message [topic: {}, id: {}]: {}", topicName, pMsg.getMessageId(),
                        new String(pMsg.getValue(), StandardCharsets.UTF_8));

                byte[] offsetBytes = topicMessageIdKey.getBytes(StandardCharsets.UTF_8);
                Offset offset = new Offset(offsetBytes);

                Map<String, String> headers = buildHeaders(pMsg);

                Message message = new Message(pMsg.getValue(), offset, Instant.now(), headers);
                observer.send(message);

                messagesToAck.put(topicMessageIdKey, pMsg);
            }
        } catch (PulsarClientException e) {
            log.error("Failed to get consumer or receive messages from Pulsar", e);
            throw new RuntimeException("Failed to get consumer or receive messages from Pulsar", e);
        }
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
            Consumer<byte[]> consumer = pulsarConsumerManager.getOrCreateConsumer(0, 0);
            consumer.acknowledge(messageIds);
            log.info("Successfully acknowledged {} messages", messageIds.size());
        } catch (PulsarClientException e) {
            log.error("Failed to acknowledge Pulsar messages", e);
        }
        messagesToAck.clear();
    }

    /**
     * Builds headers from Pulsar message metadata
     */
    private Map<String, String> buildHeaders(org.apache.pulsar.client.api.Message<byte[]> pulsarMessage) {
        Map<String, String> headers = new HashMap<>();

        headers.put(NumaHeaderKeys.PULSAR_PRODUCER_NAME, pulsarMessage.getProducerName());
        headers.put(NumaHeaderKeys.PULSAR_MESSAGE_ID, pulsarMessage.getMessageId().toString());
        headers.put(NumaHeaderKeys.PULSAR_TOPIC_NAME, pulsarMessage.getTopicName());
        headers.put(NumaHeaderKeys.PULSAR_PUBLISH_TIME, String.valueOf(pulsarMessage.getPublishTime()));
        headers.put(NumaHeaderKeys.PULSAR_EVENT_TIME, String.valueOf(pulsarMessage.getEventTime()));
        headers.put(NumaHeaderKeys.PULSAR_REDELIVERY_COUNT, String.valueOf(pulsarMessage.getRedeliveryCount()));

        // Add message properties as headers
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
