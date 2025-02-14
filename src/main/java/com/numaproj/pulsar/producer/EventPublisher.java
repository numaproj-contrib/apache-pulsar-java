package com.numaproj.pulsar.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;

import jakarta.annotation.PreDestroy;

@Slf4j
public class EventPublisher {

    private final Producer<String> producer;

    public EventPublisher(Producer<String> producer) {
        this.producer = producer;
    }

    public void publishPlainMessage(String message) {
        if (producer != null) {
            try {
                producer.send(message);
                log.info("EventPublisher::publishPlainMessage published the event: {}", message);
            } catch (PulsarClientException e) {
                log.error("Failed to send message to Pulsar", e);
            }
        } else {
            log.error("Producer is not initialized.");
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (producer != null) {
                producer.close();
                log.info("Producer closed.");
            }
        } catch (PulsarClientException e) {
            log.error("Error while closing Producer", e);
        }
    }
}
