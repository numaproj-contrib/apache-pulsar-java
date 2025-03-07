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
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarSource extends Sourcer {

    private final Map<String, org.apache.pulsar.client.api.Message<byte[]>> messages = new ConcurrentHashMap<>();

    private Server server;

    @Autowired
    private PulsarClient pulsarClient;

    @Autowired
    private Consumer<byte[]> pulsarConsumer;

    @PostConstruct
    public void startServer() throws Exception {

        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        long startTime = System.currentTimeMillis();

        // minimal check: if outstanding messages haven't been acknowledged, do nothing.
        if (!messages.isEmpty()) {
            return;
        }

        for (int i = 0; i < request.getCount(); i++) {
            if (System.currentTimeMillis() - startTime > request.getTimeout().toMillis()) {
                return;
            }

            try {
                org.apache.pulsar.client.api.Message<byte[]> pMsg = pulsarConsumer
                        .receive((int) request.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (pMsg == null) {
                    return;
                }

                log.info("Consumed Pulsar message: {}", new String(pMsg.getValue()));

                byte[] offsetBytes = pMsg.getMessageId().toString().getBytes(StandardCharsets.UTF_8);
                Offset offset = new Offset(offsetBytes);

                Map<String, String> headers = new HashMap<>();
                headers.put("pulsarMessageId", pMsg.getMessageId().toString());

                Message message = new Message(pMsg.getValue(), offset, Instant.now(), headers);

                observer.send(message);
                messages.put(pMsg.getMessageId().toString(), pMsg);

            } catch (PulsarClientException e) {
                log.error("Failed to receive message from Pulsar", e);
                return;
            }
        }
    }

    @Override
    public void ack(AckRequest request) {
        // Iterate over provided offsets and acknowledge the corresponding Pulsar
        // message.
        request.getOffsets().forEach(offset -> {
            // Convert the offset bytes back to String to get the Pulsar MessageId string.
            String messageIdKey = new String(offset.getValue(), StandardCharsets.UTF_8);
            org.apache.pulsar.client.api.Message<byte[]> pMsg = messages.get(messageIdKey);
            if (pMsg != null) {
                try {
                    pulsarConsumer.acknowledge(pMsg);
                    // Log both MessageId and payload using UTF-8 conversion for the byte array.
                    log.info("Acknowledged Pulsar message with ID: {} and payload: {}",
                            pMsg.getMessageId().toString(), new String(pMsg.getValue(), StandardCharsets.UTF_8));
                } catch (PulsarClientException e) {
                    log.error("Failed to acknowledge Pulsar message", e);
                }
                // Remove the acknowledged message from the tracking map.
                messages.remove(messageIdKey);
            }
        });
    }

    @Override
    public long getPending() {
        // number of messages not acknowledged yet
        return messages.size();
    }

    @Override
    public List<Integer> getPartitions() {
        return Sourcer.defaultPartitions(); // Fallback mechanism: a single partition based on your pod replica index.
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (pulsarConsumer != null) {
                pulsarConsumer.close();
                log.info("Consumer closed.");
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