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

import io.numaproj.pulsar.config.PulsarConsumerProperties;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * PulsarConsumerManager creates and maintains a single Consumer instance.
 * A new consumer is created based on the provided batch size and read timeout,
 * but once created it will be reused until explicitly removed.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarConsumerManager {

    @Autowired
    private PulsarConsumerProperties pulsarConsumerProperties;

    @Autowired
    private PulsarClient pulsarClient;

    // The current consumer instance.
    private Consumer<byte[]> currentConsumer;

    // Returns the current consumer if it exists. If not, creates a new one.
    public synchronized Consumer<byte[]> getOrCreateConsumer(long count, long timeoutMillis)
            throws PulsarClientException {
        if (currentConsumer != null) {
            return currentConsumer;
        }

        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();

        currentConsumer = pulsarClient.newConsumer(Schema.BYTES)
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared) // Must be shared to support multiple pods
                .subscribe();

        log.info("Created new consumer with batch size: {} and timeoutMillis: {}", count, timeoutMillis);
        return currentConsumer;
    }

    public synchronized void removeConsumer() {
        if (currentConsumer != null) {
            try {
                currentConsumer.close();
                log.info("Removed consumer.");
            } catch (PulsarClientException e) {
                log.error("Error closing consumer", e);
            }
            currentConsumer = null;
        }
    }

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

        if (pulsarClient != null) {
            try {
                pulsarClient.close();
                log.info("Pulsar client closed during cleanup.");
            } catch (PulsarClientException e) {
                log.error("Error while closing the Pulsar client in cleanup", e);
            }

        }

    }
}