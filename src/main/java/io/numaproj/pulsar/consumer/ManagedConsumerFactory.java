package io.numaproj.pulsar.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * ManagedConsumerFactory creates and maintains a single Consumer instance.
 * If a new read request comes with different read settings (batch size and
 * timeout)
 * then the existing consumer will be closed and replaced.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class ManagedConsumerFactory {

    @Autowired
    private PulsarClient pulsarClient;

    // Stores the current consumer and its settings.
    private Consumer<byte[]> currentConsumer;
    private ConsumerKey currentKey;

    /**
     * Returns the current consumer if the requested settings are the same.
     * If the settings differ or no consumer exists, creates a new one.
     *
     * @param count         maximum number of messages (batch size and receiver
     *                      queue size)
     * @param timeoutMillis the timeout in milliseconds for the batch receive policy
     * @return a Consumer instance
     * @throws PulsarClientException if consumer creation fails
     */
    public synchronized Consumer<byte[]> getOrCreateConsumer(long count, long timeoutMillis)
            throws PulsarClientException {
        ConsumerKey newKey = new ConsumerKey(count, timeoutMillis);
        // if current consumer exists and the settings are identical, reuse it.
        if (currentConsumer != null && newKey.equals(currentKey)) {
            return currentConsumer;
        }
        // Otherwise, close the old consumer if it exists.
        if (currentConsumer != null) {
            try {
                currentConsumer.close();
                log.info("Closed old consumer with key: {} before creating new consumer.", currentKey);
            } catch (PulsarClientException e) {
                log.error("Error closing previous Pulsar consumer with key: {}", currentKey, e);
            }
        }
        // Create the new consumer.
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();

        currentConsumer = pulsarClient.newConsumer(Schema.BYTES)
                .topic("testy") // TO DO: TURN TOPIC INTO SPRING BEAN if needed
                .subscriptionName("sub")
                .subscriptionType(SubscriptionType.Shared)
                .batchReceivePolicy(batchPolicy)
                .subscribe();

        currentKey = newKey;
        log.info("Created new consumer with key: {}", currentKey);
        return currentConsumer;
    }

    /**
     * Optionally, if you ever need to explicitly remove the current consumer (for
     * example, if needed within error handling),
     * this method will close it and clear the current state.
     *
     * @param count         maximum number of messages (batch size and receiver
     *                      queue size)
     * @param timeoutMillis the timeout in milliseconds used when creating the
     *                      consumer
     */
    public synchronized void removeConsumer(long count, long timeoutMillis) {
        ConsumerKey keyToRemove = new ConsumerKey(count, timeoutMillis);
        if (currentConsumer != null && keyToRemove.equals(currentKey)) {
            try {
                currentConsumer.close();
                log.info("Removed consumer with key: {}", currentKey);
            } catch (PulsarClientException e) {
                log.error("Error closing consumer with key: {}", currentKey, e);
            }
            currentConsumer = null;
            currentKey = null;
        }
    }

    /**
     * Closes the consumer during application shutdown.
     */
    @PreDestroy
    public synchronized void cleanup() {
        if (currentConsumer != null) {
            try {
                currentConsumer.close();
                log.info("Consumer closed during cleanup.");
            } catch (PulsarClientException e) {
                log.error("Error while closing consumer in cleanup", e);
            }
        }
    }

    /**
     * A composite key class to uniquely identify consumer settings.
     */
    private static class ConsumerKey {
        private final long count;
        private final long timeoutMillis;

        ConsumerKey(long count, long timeoutMillis) {
            this.count = count;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ConsumerKey))
                return false;
            ConsumerKey that = (ConsumerKey) o;
            return count == that.count && timeoutMillis == that.timeoutMillis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(count, timeoutMillis);
        }

        @Override
        public String toString() {
            return "ConsumerKey{" +
                    "count=" + count +
                    ", timeoutMillis=" + timeoutMillis +
                    '}';
        }
    }
}