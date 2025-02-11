package com.numaproj.pulsar.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
@Slf4j
public class EventPublisher {

    @Value("${spring.pulsar.client.service-url}")
    private String serviceUrl;

    @Value("${spring.pulsar.producer.topic-name}")
    private String topicName;

    private PulsarClient pulsarClient;
    private Producer<String> producer;

    @PostConstruct
    public void init() {
        try {
            pulsarClient = PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .build();
            producer = pulsarClient.newProducer(Schema.STRING)
                    .topic(topicName)
                    .create();
            log.info("PulsarClient and Producer are successfully instantiated.");
            log.info("Loaded topic name: {}", topicName);
        } catch (PulsarClientException e) {
            log.error("Failed to create PulsarClient or Producer", e);
        }
    }

    public void publishPlainMessage(String message) {
        if (producer != null) {
            try {
                producer.send(message);
                log.info("EventPublisher::publishPlainMessage published the event {}", message);
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

            if (pulsarClient != null) {
                pulsarClient.close();
                log.info("PulsarClient closed.");
            }
        } catch (PulsarClientException e) {
            log.error("Error while closing PulsarClient or Producer", e);
        }
    }
}
