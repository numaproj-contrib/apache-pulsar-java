package io.numaproj.pulsar.consumer;

import com.google.common.primitives.Longs;
import io.numaproj.numaflow.sourcer.AckRequest;
import io.numaproj.numaflow.sourcer.Message;
import io.numaproj.numaflow.sourcer.Offset;
import io.numaproj.numaflow.sourcer.OutputObserver;
import io.numaproj.numaflow.sourcer.ReadRequest;
import io.numaproj.numaflow.sourcer.Server;
import io.numaproj.numaflow.sourcer.Sourcer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class SimpleSource extends Sourcer {

    // Mapping of readIndex to Pulsar messages that haven't yet been acknowledged
    private final Map<Long, org.apache.pulsar.client.api.Message<byte[]>> messages = new ConcurrentHashMap<>();
    private long readIndex = 0;
    private Server server;

    @Autowired
    private PulsarClient pulsarClient;

    // Consumer for Pulsar messages on hardcoded topic "my-topic"
    private Consumer<byte[]> pulsarConsumer;

    @PostConstruct // starts server automatically when the Spring context initializes
    public void startServer() throws Exception {
        // Create and start Pulsar consumer using the autowired PulsarClient.
        pulsarConsumer = pulsarClient.newConsumer(Schema.BYTES)
                .topic("my-topic")
                .subscriptionName("my-topic-subscription")
                .subscribe();

        // Start the gRPC server for reading messages.
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
            // stop reading if timeout exceeds the allowed duration.
            if (System.currentTimeMillis() - startTime > request.getTimeout().toMillis()) {
                return;
            }

            try {
                // Correctly cast the timeout to int as the receive method expects an int.
                org.apache.pulsar.client.api.Message<byte[]> pMsg = pulsarConsumer.receive((int) request.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (pMsg == null) {
                    // no message received within timeout
                    return;
                }

                // Log the consumed message (converting byte[] to String for logging clarity)
                log.info("Consumed Pulsar message: {}", new String(pMsg.getValue()));

                // Create a header map with additional Pulsar message id detail if needed.
                Map<String, String> headers = new HashMap<>();
                headers.put("pulsarMessageId", pMsg.getMessageId().toString());

                // create a new message wrapping the received Pulsar message value
                Offset offset = new Offset(Longs.toByteArray(readIndex));
                Message message = new Message(pMsg.getValue(), offset, Instant.now(), headers);

                // send the message to the observer
                observer.send(message);

                // keep track of the pulsar message that's been read and not yet acknowledged
                messages.put(readIndex, pMsg);
                readIndex += 1;
            } catch (PulsarClientException e) {
                log.error("Failed to receive message from Pulsar", e);
                return;
            }
        }
    }

    @Override
    public void ack(AckRequest request) {
        // Iterate over provided offsets and acknowledge corresponding Pulsar message.
        request.getOffsets().forEach(offset -> {
            Long decodedOffset = Longs.fromByteArray(offset.getValue());
            org.apache.pulsar.client.api.Message<byte[]> pMsg = messages.get(decodedOffset);
            if (pMsg != null) {
                try {
                    pulsarConsumer.acknowledge(pMsg);
                    log.info("Acknowledged Pulsar message with ID: {}", pMsg.getMessageId().toString());
                } catch (PulsarClientException e) {
                    log.error("Failed to acknowledge Pulsar message", e);
                }
                // remove the acknowledged message from the tracking map.
                messages.remove(decodedOffset);
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
        return Sourcer.defaultPartitions();
    }
}