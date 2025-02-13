package com.numaproj.pulsar.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.numaproj.pulsar.config.PulsarClientProperties;
import com.numaproj.pulsar.config.PulsarProducerProperties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
@Slf4j
public class EventPublisher {

    private final PulsarClientProperties pulsarClientProperties;
    private final PulsarProducerProperties pulsarProducerProperties;

    private PulsarClient pulsarClient;
    private Producer<String> producer;

    @Autowired
    public EventPublisher(PulsarClientProperties pulsarClientProperties,
            PulsarProducerProperties pulsarProducerProperties) {
        this.pulsarClientProperties = pulsarClientProperties;
        this.pulsarProducerProperties = pulsarProducerProperties;
    }

    @PostConstruct
    public void init() {
        try {
            pulsarClient = PulsarClient.builder()
                    .loadConf(pulsarClientProperties.getClientConfig())
                    .build();

            producer = pulsarClient.newProducer(Schema.STRING)
                    .loadConf(pulsarProducerProperties.getProducerConfig())
                    .create();

            log.info("PulsarClient and Producer are successfully instantiated.");
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
