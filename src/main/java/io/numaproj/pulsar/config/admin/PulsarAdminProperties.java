package io.numaproj.pulsar.config.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.pulsar.admin")
public class PulsarAdminProperties {
    private Map<String, Object> adminConfig = new HashMap<>(); // Admin-specific configuration map
}
