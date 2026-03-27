package io.numaproj.pulsar.config.client;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

/**
 * Builds a PulsarClient from configuration.
 */
public final class PulsarClientConfig {

    // Private constructor to prevent instantiation
    private PulsarClientConfig() {
    }

    public static PulsarClient create(PulsarClientProperties pulsarClientProperties) throws PulsarClientException {
        return PulsarClient.builder()
                .loadConf(pulsarClientProperties.getClientConfig())
                .build();
    }
}