package io.numaproj.pulsar.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.schema.GenericRecord;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;

import java.util.concurrent.TimeUnit;

/**
 * Lazily creates and caches the Pulsar consumer used by PulsarSource.
 * Supports two consumer shapes: raw byte[] (Schema.BYTES) or GenericRecord (Schema.AUTO_CONSUME).
 * Both consumers use Shared subscriptions so multiple pods can consume the same topic concurrently.
 */
@Slf4j
public class PulsarConsumerManager {

    private final PulsarConsumerProperties pulsarConsumerProperties;
    private final PulsarClient pulsarClient;

    /**
     * Creates a new manager. Consumers are created lazily on first use.
     *
     * @param pulsarConsumerProperties parsed pulsar.consumer section
     * @param pulsarClient             the Pulsar client
     */
    public PulsarConsumerManager(PulsarConsumerProperties pulsarConsumerProperties, PulsarClient pulsarClient) {
        this.pulsarConsumerProperties = pulsarConsumerProperties;
        this.pulsarClient = pulsarClient;
    }

    private Consumer<byte[]> bytesConsumer;
    private Consumer<GenericRecord> genericRecordConsumer;

    /**
     * Returns the byte-array consumer, creating it on first call.
     *
     * @param count         maximum number of messages per batch
     * @param timeoutMillis maximum time to wait for the batch to fill
     * @return the byte-array consumer
     * @throws PulsarClientException if consumer creation fails
     */
    public Consumer<byte[]> getOrCreateBytesConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (bytesConsumer != null) {
            return bytesConsumer;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS) // We do not expect user to specify a number larger than 2^63 - 1 which will cause an overflow
                .build();
        bytesConsumer = pulsarClient.newConsumer(Schema.BYTES)
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared) // Must be shared to support multiple pods
                .subscribe();
        log.info("Created byte-array consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return bytesConsumer;
    }

    /**
     * Returns the AUTO_CONSUME (GenericRecord) consumer, creating it on first call.
     * Each message is validated against the registered topic schema.
     *
     * @param count         maximum number of messages per batch
     * @param timeoutMillis maximum time to wait for the batch to fill
     * @return the schema-aware consumer
     * @throws PulsarClientException if consumer creation fails
     */
    public Consumer<GenericRecord> getOrCreateGenericRecordConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (genericRecordConsumer != null) {
            return genericRecordConsumer;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS) // We do not expect user to specify a number larger than 2^63 - 1 which will cause an overflow
                .build();
        genericRecordConsumer = pulsarClient.newConsumer(Schema.AUTO_CONSUME())
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared) // Must be shared to support multiple pods
                .subscribe();
        log.info("Created AUTO_CONSUME (GenericRecord) consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return genericRecordConsumer;
    }

    /**
     * Closes any created consumers and the underlying Pulsar client.
     * Individual close failures are logged rather than thrown.
     */
    public void cleanup() {
        if (bytesConsumer != null) {
            try {
                bytesConsumer.close();
                log.info("Byte-array consumer closed.");
            } catch (Exception e) {
                log.error("Error closing byte-array consumer", e);
            }
            bytesConsumer = null;
        }
        if (genericRecordConsumer != null) {
            try {
                genericRecordConsumer.close();
                log.info("GenericRecord consumer closed.");
            } catch (Exception e) {
                log.error("Error closing GenericRecord consumer", e);
            }
            genericRecordConsumer = null;
        }
        if (pulsarClient != null) {
            try {
                pulsarClient.close();
                log.info("Pulsar client closed.");
            } catch (PulsarClientException e) {
                log.error("Error closing Pulsar client", e);
            }
        }
    }
}
