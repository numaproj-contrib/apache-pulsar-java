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

import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;

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

    // The current consumer instance (either Consumer<byte[]> or Consumer<GenericRecord>).
    private Consumer<?> currentConsumer;

    /**
     * Returns the current consumer if it exists. If not, creates a new one.
     * When {@link PulsarConsumerProperties#isUseAutoConsumeSchema()} is true, returns
     * Consumer&lt;GenericRecord&gt;; otherwise Consumer&lt;byte[]&gt;.
     */
    @SuppressWarnings("unchecked")
    public <T> Consumer<T> getOrCreateConsumer(long count, long timeoutMillis)
            throws PulsarClientException {
        if (currentConsumer != null) {
            return (Consumer<T>) currentConsumer;
        }

        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS) // We do not expect user to specify a number larger
                                                                     // than 2^63 - 1 which will cause an overflow
                .build();

        boolean useAutoConsume = pulsarConsumerProperties.isUseAutoConsumeSchema();
        Schema<?> schema = useAutoConsume ? Schema.AUTO_CONSUME() : Schema.BYTES;
        String schemaLabel = useAutoConsume ? "AUTO_CONSUME (schema validation enabled)" : "BYTES (no schema validation)";

        currentConsumer = pulsarClient.newConsumer(schema)
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared)
                .subscribe();
        log.info("Created new consumer with Schema.{}; batch receive policy: {}, timeoutMillis: {}", schemaLabel, count, timeoutMillis);
        return (Consumer<T>) currentConsumer;
    }

    @PreDestroy
    public void cleanup() {
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