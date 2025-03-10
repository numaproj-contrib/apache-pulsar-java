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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    // Map tracking received messages (keyed by Pulsar message ID string)
    private final Map<String, org.apache.pulsar.client.api.Message<byte[]>> messagesToAck = new ConcurrentHashMap<>();

    private Server server;

    // Inject the managed consumer factory.
    @Autowired
    private PulsarConsumerManager pulsarConsumerManager;

    @PostConstruct
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        // if there are messages not acknowledged, return
        if (!messagesToAck.isEmpty()) {
            return;
        }

        Consumer<byte[]> consumer = null;

        try {
            // Obtain a consumer with the desired settings.
            consumer = pulsarConsumerManager.getOrCreateConsumer(request.getCount(), request.getTimeout().toMillis());
            Messages<byte[]> batchMessages = null;
            try {
                batchMessages = consumer.batchReceive();
            } catch (PulsarClientException.AlreadyClosedException ace) {
                // If the consumer is closed, remove it and try to obtain a new one.
                log.warn("Cached consumer was closed. Removing and recreating consumer.");
                pulsarConsumerManager.removeConsumer();
                consumer = pulsarConsumerManager.getOrCreateConsumer(request.getCount(),
                        request.getTimeout().toMillis());
                batchMessages = consumer.batchReceive();
            }

            if (batchMessages == null || batchMessages.size() == 0) {
                return;
            }

            // Process each message in the batch.
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

                messagesToAck.put(msgId, pMsg);
            }
        } catch (PulsarClientException e) {
            log.error("Failed to get consumer or receive messages from Pulsar", e);
        }
    }

    @Override
    public void ack(AckRequest request) {
        request.getOffsets().forEach(offset -> {
            String messageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
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
            }
        });
    }

    @Override
    public long getPending() {
        return messagesToAck.size();
    }

    @Override
    public List<Integer> getPartitions() {
        return Sourcer.defaultPartitions();
    }
}