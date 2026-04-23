package io.numaproj.pulsar.config.admin;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.Map;

/**
 * Builds a PulsarAdmin from configuration.
 */
public final class PulsarAdminConfig {

    private PulsarAdminConfig() {
    }

    /**
     * Builds a PulsarAdmin from the supplied properties.
     *
     * @param pulsarAdminProperties parsed pulsar.admin config
     * @return the admin client
     * @throws IllegalStateException if the admin config is empty
     * @throws PulsarClientException if the admin client cannot be built
     */
    public static PulsarAdmin create(PulsarAdminProperties pulsarAdminProperties) throws PulsarClientException {
        Map<String, Object> config = pulsarAdminProperties.getAdminConfig();
        if (config.isEmpty()) {
            throw new IllegalStateException("Pulsar admin configuration is required but not provided");
        }
        return PulsarAdmin.builder()
                .loadConf(config)
                .build();
    }
}
