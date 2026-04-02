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
