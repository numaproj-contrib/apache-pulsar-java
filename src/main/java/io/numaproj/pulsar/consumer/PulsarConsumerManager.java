package io.numaproj.pulsar.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
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
import java.util.List;
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

    private Consumer<byte[]> consumerBytes;
    private Consumer<GenericRecord> consumerGenericRecord;

    /** Returns the byte-array consumer, creating it if necessary. Use when not using AUTO_CONSUME. */
    public Consumer<byte[]> getOrCreateBytesConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (consumerBytes != null) {
            return consumerBytes;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        consumerBytes = pulsarClient.newConsumer(Schema.BYTES)
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared)
                .subscribe();
        log.info("Created byte-array consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return consumerBytes;
    }

    /** Returns the GenericRecord (AUTO_CONSUME) consumer, creating it if necessary. Use when using AUTO_CONSUME. */
    public Consumer<GenericRecord> getOrCreateGenericRecordConsumer(long count, long timeoutMillis) throws PulsarClientException {
        if (consumerGenericRecord != null) {
            return consumerGenericRecord;
        }
        BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages((int) count)
                .timeout((int) timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        consumerGenericRecord = pulsarClient.newConsumer(Schema.AUTO_CONSUME())
                .loadConf(pulsarConsumerProperties.getConsumerConfig())
                .batchReceivePolicy(batchPolicy)
                .subscriptionType(SubscriptionType.Shared)
                .subscribe();
        log.info("Created AUTO_CONSUME (GenericRecord) consumer; batch receive: {}, timeoutMillis: {}", count, timeoutMillis);
        return consumerGenericRecord;
    }

    /** Acknowledges the given message IDs on the consumer that matches the current config (bytes vs AUTO_CONSUME). */
    public void acknowledge(List<MessageId> messageIds) throws PulsarClientException {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        if (pulsarConsumerProperties.isUseAutoConsumeSchema()) {
            if (consumerGenericRecord != null) {
                consumerGenericRecord.acknowledge(messageIds);
            }
        } else {
            if (consumerBytes != null) {
                consumerBytes.acknowledge(messageIds);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (consumerBytes != null) {
            try {
                consumerBytes.close();
                log.info("Byte-array consumer closed.");
            } catch (Exception e) {
                log.error("Error closing byte-array consumer", e);
            }
            consumerBytes = null;
        }
        if (consumerGenericRecord != null) {
            try {
                consumerGenericRecord.close();
                log.info("GenericRecord consumer closed.");
            } catch (Exception e) {
                log.error("Error closing GenericRecord consumer", e);
            }
            consumerGenericRecord = null;
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
