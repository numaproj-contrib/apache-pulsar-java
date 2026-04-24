package io.numaproj.pulsar.config.client;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the pulsar.client configuration parsed from application.yml.
 * Default to an empty map.
 */
@Getter
@Setter
public class PulsarClientProperties {
    private Map<String, Object> clientConfig = new HashMap<>(); // Default to an empty map
}
