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
 * Creates and maintains Pulsar consumers: one for byte[] (Schema.BYTES) and one for
 * schema-backed messages (Schema.AUTO_CONSUME / GenericRecord). Only the consumer
 * matching the configured schema is created and used; the other remains null.
 */
@Slf4j
public class PulsarConsumerManager {

    private final PulsarConsumerProperties pulsarConsumerProperties;
    private final PulsarClient pulsarClient;

    public PulsarConsumerManager(PulsarConsumerProperties pulsarConsumerProperties, PulsarClient pulsarClient) {
        this.pulsarConsumerProperties = pulsarConsumerProperties;
        this.pulsarClient = pulsarClient;
    }

    private Consumer<byte[]> bytesConsumer;
    private Consumer<GenericRecord> genericRecordConsumer;

    /** Returns the byte-array consumer, creating it if necessary. Use when not using AUTO_CONSUME. */
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
        log.atInfo().setMessage("Created byte-array consumer.")
                .addKeyValue("batchSize", count)
                .addKeyValue("timeoutMillis", timeoutMillis).log();
        return bytesConsumer;
    }

    /** Returns the GenericRecord (AUTO_CONSUME) consumer, creating it if necessary. Use when using AUTO_CONSUME. */
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
        log.atInfo().setMessage("Created AUTO_CONSUME (GenericRecord) consumer.")
                .addKeyValue("batchSize", count)
                .addKeyValue("timeoutMillis", timeoutMillis).log();
        return genericRecordConsumer;
    }

    public void cleanup() {
        if (bytesConsumer != null) {
            try {
                bytesConsumer.close();
                log.atInfo().setMessage("Byte-array consumer closed.").log();
            } catch (Exception e) {
                log.atError().setMessage("Error closing byte-array consumer.").setCause(e).log();
            }
            bytesConsumer = null;
        }
        if (genericRecordConsumer != null) {
            try {
                genericRecordConsumer.close();
                log.atInfo().setMessage("GenericRecord consumer closed.").log();
            } catch (Exception e) {
                log.atError().setMessage("Error closing GenericRecord consumer.").setCause(e).log();
            }
            genericRecordConsumer = null;
        }
        if (pulsarClient != null) {
            try {
                pulsarClient.close();
                log.atInfo().setMessage("Pulsar client closed.").log();
            } catch (PulsarClientException e) {
                log.atError().setMessage("Error closing Pulsar client.").setCause(e).log();
            }
        }
    }
}
