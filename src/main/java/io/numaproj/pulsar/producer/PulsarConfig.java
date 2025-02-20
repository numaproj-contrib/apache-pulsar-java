package io.numaproj.pulsar.producer;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.Producer;
import org.springframework.boot.autoconfigure.pulsar.PulsarProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.numaproj.pulsar.config.PulsarClientProperties;
import io.numaproj.pulsar.config.PulsarProducerProperties;

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

    @Bean
    public PulsarClient pulsarClient(PulsarClientProperties properties) throws PulsarClientException {
        return PulsarClient.builder()
                .loadConf(properties.getClientConfig())
                .build();
    }

    @Bean
    public Producer<byte[]> pulsarProducer(PulsarClient pulsarClient, PulsarProducerProperties pulsarProducerProperties)
            throws Exception {
        return pulsarClient.newProducer(Schema.BYTES)
                .loadConf(pulsarProducerProperties.getProducerConfig())
                .create();
    }

}
