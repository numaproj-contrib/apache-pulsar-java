package io.numaproj.pulsar.config.admin;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class PulsarAdminConfig {
    @Bean
    public PulsarAdmin pulsarAdmin(PulsarAdminProperties pulsarAdminProperties) throws PulsarClientException {
        Map<String, Object> config = pulsarAdminProperties.getAdminConfig();
        
        if (config.isEmpty()) {
            throw new IllegalStateException("Pulsar admin configuration is required but not provided");
        }
        
        return PulsarAdmin.builder()
                .loadConf(config)
                .build();
    }
}