package io.numaproj.pulsar.config.client;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class PulsarClientProperties {
    private Map<String, Object> clientConfig = new HashMap<>(); // Default to an empty map
}
