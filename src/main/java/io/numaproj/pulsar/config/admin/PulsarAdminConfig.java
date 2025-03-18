package io.numaproj.pulsar.config.admin;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class PulsarAdminConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.pulsar.admin", name = "enabled", havingValue = "true", matchIfMissing = false)
    public PulsarAdmin pulsarAdmin(PulsarAdminProperties pulsarAdminProperties) throws PulsarClientException {
        return PulsarAdmin.builder()
                .loadConf(pulsarAdminProperties.getAdminConfig())
                .build();
    }
}