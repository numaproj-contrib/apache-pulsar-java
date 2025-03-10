package io.numaproj.pulsar.consumer;

import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.OutputObserver;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Server;
import io.numaproj.numaflow.sourcer.Sourcer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * PulsarSource is responsible for consuming messages from Pulsar.
 * In this refactored version, the consumer is solely managed by the
 * ManagedConsumerFactory.
 * This implementation does not track consumer usage or call consumer.close() in
 * ack.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    // Map tracking received messages (keyed by Pulsar message ID string)
    private final Map<String, org.apache.pulsar.client.api.Message<byte[]>> messages = new ConcurrentHashMap<>();
    // Map tracking the consumer instance that received each message. In the new
    // design, each message
    // should have been received by the current consumer instance.

    /**
     * this map stores a relationship between a message ID (as a string) and the
     * specific consumer instance that received it.
     * during ack, this mapping allows the code to look up the correct
     * consumer instance to call the acknowledge() method, ensuring the proper
     * handling of each message.
     */
    private final Map<String, Consumer<byte[]>> consumerByMessage = new ConcurrentHashMap<>();

    private Server server;

    // Inject the managed consumer factory.
    @Autowired
    private ManagedConsumerFactory managedConsumerFactory;

    @PostConstruct
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        Consumer<byte[]> consumer = null;
        try {
            // Obtain a consumer with the desired settings.
            consumer = managedConsumerFactory.getOrCreateConsumer(request.getCount(), request.getTimeout().toMillis());
            Messages<byte[]> batchMessages = null;
            try {
                // Attempt to receive a batch.
                batchMessages = consumer.batchReceive();
            } catch (PulsarClientException.AlreadyClosedException ace) {
                // If the consumer is closed, remove it and try to obtain a new one.
                log.warn("Cached consumer was closed. Removing and recreating consumer.");
                managedConsumerFactory.removeConsumer(request.getCount(), request.getTimeout().toMillis());
                consumer = managedConsumerFactory.getOrCreateConsumer(request.getCount(),
                        request.getTimeout().toMillis());
                batchMessages = consumer.batchReceive();
            }

            if (batchMessages == null || batchMessages.size() == 0) {
                return;
            }

            // Process each message in the trimmed batch.
            for (org.apache.pulsar.client.api.Message<byte[]> pMsg : batchMessages) {
                String msgId = pMsg.getMessageId().toString();
                log.info("Consumed Pulsar message [id: {}]: {}", pMsg.getMessageId(),
                        new String(pMsg.getValue(), StandardCharsets.UTF_8));

                byte[] offsetBytes = msgId.getBytes(StandardCharsets.UTF_8);
                Offset offset = new Offset(offsetBytes);

                HashMap<String, String> headers = new HashMap<>();
                headers.put("pulsarMessageId", msgId);

                Message message = new Message(pMsg.getValue(), offset, Instant.now(), headers);
                observer.send(message);

                // Track message and the consumer that received it.
                messages.put(msgId, pMsg);
                consumerByMessage.put(msgId, consumer);
            }
        } catch (PulsarClientException e) {
            log.error("Failed to get consumer or receive messages from Pulsar", e);
        }
    }

    @Override
    public void ack(AckRequest request) {
        request.getOffsets().forEach(offset -> {
            String messageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
            org.apache.pulsar.client.api.Message<byte[]> pMsg = messages.get(messageIdKey);
            Consumer<byte[]> consumer = consumerByMessage.get(messageIdKey);
            if (pMsg != null && consumer != null) {
                try {
                    consumer.acknowledge(pMsg);
                    log.info("Acknowledged Pulsar message with ID: {} and payload: {}",
                            messageIdKey, new String(pMsg.getValue(), StandardCharsets.UTF_8));
                } catch (PulsarClientException e) {
                    log.error("Failed to acknowledge Pulsar message", e);
                }
                // Remove acknowledged message from tracking maps.
                messages.remove(messageIdKey);
                consumerByMessage.remove(messageIdKey);
            }
        });
    }

    @Override
    public long getPending() {
        // Number of messages not acknowledged yet.
        return messages.size();
    }

    @Override
    public List<Integer> getPartitions() {
        return Sourcer.defaultPartitions();
    }

    @PreDestroy
    public void cleanup() {
        // Close the current consumer during shutdown.
        try {
            managedConsumerFactory.removeConsumer(0, 0);
            log.info("Managed consumer removed during cleanup.");
        } catch (Exception e) {
            log.error("Error while closing consumer in cleanup", e);
        }
    }
}