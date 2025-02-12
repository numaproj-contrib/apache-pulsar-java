package com.numaproj.pulsar.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;

@Component
@Slf4j
public class EventPublisher {

    @Value("${spring.pulsar.client.service-url}")
    private String serviceUrl;

    private final PulsarProducerProperties pulsarProducerProperties;

    private PulsarClient pulsarClient;
    private Producer<String> producer;

    @Autowired
    public EventPublisher(PulsarProducerProperties pulsarProducerProperties) {
        this.pulsarProducerProperties = pulsarProducerProperties;
    }

    @PostConstruct
    public void init() {
        try {
            pulsarClient = PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .build();

            producer = pulsarClient.newProducer(Schema.STRING)
                    .topic(pulsarProducerProperties.getTopicName())
                    .loadConf(pulsarProducerProperties.getProducerConfig())
                    .create();

            log.info("PulsarClient and Producer are successfully instantiated.");
            log.info("Loaded topic name: {}", pulsarProducerProperties.getTopicName());

            // Log the key-value pairs in the producerConfig map
            log.info("Producer configuration loaded:");
            for (Map.Entry<String, Object> entry : pulsarProducerProperties.getProducerConfig().entrySet()) {
                log.info("Key: {}, Value: {}", entry.getKey(), entry.getValue());
            }
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
