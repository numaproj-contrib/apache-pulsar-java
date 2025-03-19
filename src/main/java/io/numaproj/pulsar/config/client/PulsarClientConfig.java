package io.numaproj.pulsar.config.client;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PulsarClientConfig {

    @Bean
    public PulsarClient pulsarClient(PulsarClientProperties pulsarClientProperties) throws PulsarClientException {
        return PulsarClient.builder()
                .loadConf(pulsarClientProperties.getClientConfig())
                .build();
    }
}