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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SimpleSource is a simple implementation of Sourcer.
 * It generates messages with increasing offsets.
 * Keeps track of the messages read and acknowledges them when ack is called.
 *
 * This component is conditionally loaded only when consumer properties are
 * enabled.
 * For example, when 'spring.pulsar.consumer.enabled' is set to truÅe.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class SimpleSource extends Sourcer {

    private final Map<Long, Boolean> messages = new ConcurrentHashMap<>();
    private long readIndex = 0;
    private Server server;

    @PostConstruct // starts server automatically when the Spring context initializes
    public void startServer() throws Exception {
        server = new Server(this);
        server.start();
        server.awaitTermination();
    }

    @Override
    public void read(ReadRequest request, OutputObserver observer) {
        long startTime = System.currentTimeMillis();

        // if there are messages not acknowledged, return
        if (!messages.isEmpty()) {
            return;
        }

        for (int i = 0; i < request.getCount(); i++) {
            if (System.currentTimeMillis() - startTime > request.getTimeout().toMillis()) {
                return;
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("x-txn-id", UUID.randomUUID().toString());

            // create a message with increasing offset
            Offset offset = new Offset(Longs.toByteArray(readIndex));
            Message message = new Message(
                    Long.toString(readIndex).getBytes(),
                    offset,
                    Instant.now(),
                    headers);

            // send the message to the observer
            observer.send(message);
            // keep track of the messages read and not acknowledged
            messages.put(readIndex, true);
            readIndex += 1;
        }
    }

    @Override
    public void ack(AckRequest request) {
        request.getOffsets().forEach(offset -> {
            Long decodedOffset = Longs.fromByteArray(offset.getValue());
            // remove the acknowledged messages from the map
            messages.remove(decodedOffset);
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