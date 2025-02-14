package com.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.numaproj.pulsar.config.PulsarClientProperties;
import com.numaproj.pulsar.config.PulsarProducerProperties;

/***
 *
 * For each @Configuration annotated class it finds, Spring will create an
 * instance of that configuration class
 * and then call the methods marked with @Bean to get the instances of beans.
 * The objects returned by these methods are then registered within the Spring
 * application context.
 */
@Configuration
public class PulsarConfig {

    @Autowired
    private PulsarClientProperties pulsarClientProperties;

    @Bean
    public PulsarClient pulsarClient() throws PulsarClientException {
        return PulsarClient.builder()
                .loadConf(pulsarClientProperties.getClientConfig())
                .build();
    }

    // TO DO: Extend the conditional logic when implementing sink
    @Configuration
    @ConditionalOnProperty(name = "spring.pulsar.producer.enable", havingValue = "true", matchIfMissing = true)
    static class ProducerConfig {

        @Autowired
        private PulsarProducerProperties pulsarProducerProperties;

        @Autowired
        private PulsarClient pulsarClient;

        @Bean
        public Producer<String> pulsarProducer() throws PulsarClientException {
            return pulsarClient.newProducer(Schema.STRING)
                    .loadConf(pulsarProducerProperties.getProducerConfig())
                    .create();
        }
    
    }
}