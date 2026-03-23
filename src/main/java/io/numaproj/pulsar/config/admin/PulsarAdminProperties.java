package io.numaproj.pulsar.config.admin;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class PulsarAdminProperties {
    private Map<String, Object> adminConfig = new HashMap<>(); // Admin-specific configuration map
}
