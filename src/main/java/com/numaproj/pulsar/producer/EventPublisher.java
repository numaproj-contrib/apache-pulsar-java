package com.numaproj.pulsar.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class EventPublisher {


    @Value("${spring.pulsar.producer.topic-name1}")
    private String topicName1;

    @PostConstruct
    public void init() {
        if (pulsarTemplate == null) {
            log.error("PulsarTemplate is not instantiated!");
        } else {
            log.info("PulsarTemplate is successfully ≈.");
        }
        log.info("Loaded topic name: {}", topicName1);
    }

    @Autowired
    private PulsarTemplate<Object> pulsarTemplate;


    public void publishPlainMessage(String message) throws PulsarClientException {
        pulsarTemplate.send(topicName1, message);
        log.info("EventPublisher::publishPlainMessage publish the event {}", message);
    }


}
