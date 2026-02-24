package io.numaproj.pulsar.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Creates and maintains Pulsar consumers: one for byte[] (Schema.BYTES) and one for
 * schema-backed messages (Schema.AUTO_CONSUME / GenericRecord). Only the consumer
 * matching the configured schema is created and used; the other remains null.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.pulsar.consumer", name = "enabled", havingValue = "true")
public class PulsarConsumerManager {

    @Autowired
    private PulsarConsumerProperties pulsarConsumerProperties;

    @Autowired
    private PulsarClient pulsarClient;

    private Consumer<byte[]> bytesConsumer;
    private Consumer<GenericRecord> genericRecordConsumer;

    /** Returns the byte-array consumer, creating it if necessary. Use when not using AUTO_CONSUME. */
    public Consumer<byte[]> getOrCreateBytesConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (bytesConsumer != null) {
            return bytesConsumer;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        bytesConsumer = pulsarClient.newConsumer(Schema.BYTES)
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared) // Must be shared to support multiple pods
                .subscribe();
        log.info("Created byte-array consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return bytesConsumer;
    }

    /** Returns the GenericRecord (AUTO_CONSUME) consumer, creating it if necessary. Use when using AUTO_CONSUME. */
    public Consumer<GenericRecord> getOrCreateGenericRecordConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (genericRecordConsumer != null) {
            return genericRecordConsumer;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        genericRecordConsumer = pulsarClient.newConsumer(Schema.AUTO_CONSUME())
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared) // Must be shared to support multiple pods
                .subscribe();
        log.info("Created AUTO_CONSUME (GenericRecord) consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return genericRecordConsumer;
    }

    @PreDestroy
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
