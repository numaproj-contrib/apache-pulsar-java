package io.numaproj.pulsar.config;

import io.numaproj.pulsar.config.admin.PulsarAdminProperties;
import io.numaproj.pulsar.config.client.PulsarClientProperties;
import io.numaproj.pulsar.config.consumer.PulsarConsumerProperties;
import io.numaproj.pulsar.config.producer.PulsarProducerProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PulsarConfigLoader {

    // Pattern defines a regex used to find placeholders like ${VAR} or ${VAR:default} in config strings so they can be replaced
    // (e.g. authParams: "${PULSAR_AUTH_TOKEN}").
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    // Loads Pulsar config from the given path.
    public static LoadedPulsarConfig loadConfig(Path configPath) throws IOException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return loadConfig(in);
        }
    }

    // Loads Pulsar config from the given YAML input stream.
    public static LoadedPulsarConfig loadConfig(InputStream yamlInputStream) {
        Yaml yaml = new Yaml();

        // Loads the YAML document from the input stream.
        Map<String, Object> root = yaml.load(yamlInputStream);
        if (root == null) {
            root = new HashMap<>();
        }

        Map<String, Object> resolvedConfig = resolvePlaceholders(root);
        Map<String, Object> pulsar = getMap(resolvedConfig, "pulsar");

        PulsarClientProperties clientProperties = loadClientProperties(getMap(pulsar, "client"));
        PulsarAdminProperties adminProperties = loadAdminProperties(getMap(pulsar, "admin"));
        PulsarConsumerProperties consumerProperties = loadConsumerProperties(getMap(pulsar, "consumer"));
        PulsarProducerProperties producerProperties = loadProducerProperties(getMap(pulsar, "producer"));

        return new LoadedPulsarConfig(clientProperties, adminProperties, consumerProperties, producerProperties);
    }

    // Safely gets a nested map by key. Returns an empty map if the key is missing,
    // null, or not a map.
    // SuppressWarnings: the cast is safe because we guard with instanceof Map.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map != null ? map.get(key) : null;
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return new HashMap<>();
    }

    private static PulsarClientProperties loadClientProperties(Map<String, Object> section) {
        PulsarClientProperties p = new PulsarClientProperties();
        Map<String, Object> clientConfig = getMap(section, "clientConfig");
        p.setClientConfig(deepCopyMap(clientConfig));
        return p;
    }

    private static PulsarAdminProperties loadAdminProperties(Map<String, Object> section) {
        PulsarAdminProperties p = new PulsarAdminProperties();
        Map<String, Object> adminConfig = getMap(section, "adminConfig");
        p.setAdminConfig(deepCopyMap(adminConfig));
        return p;
    }

    private static PulsarConsumerProperties loadConsumerProperties(Map<String, Object> section) {
        PulsarConsumerProperties p = new PulsarConsumerProperties();
        if (section.containsKey("enabled")) {
            p.setEnabled(toBoolean(section.get("enabled"), p.isEnabled()));
        }
        if (section.containsKey("useAutoConsumeSchema")) {
            p.setUseAutoConsumeSchema(
                    toBoolean(section.get("useAutoConsumeSchema"), p.isUseAutoConsumeSchema()));
        }
        Map<String, Object> consumerConfig = getMap(section, "consumerConfig");
        p.setConsumerConfig(deepCopyMap(consumerConfig));
        return p;
    }

    private static PulsarProducerProperties loadProducerProperties(Map<String, Object> section) {
        PulsarProducerProperties p = new PulsarProducerProperties();
        if (section.containsKey("enabled")) {
            p.setEnabled(toBoolean(section.get("enabled"), p.isEnabled()));
        }
        if (section.containsKey("useAutoProduceSchema")) {
            p.setUseAutoProduceSchema(toBoolean(section.get("useAutoProduceSchema"), p.isUseAutoProduceSchema()));
        }
        if (section.containsKey("dropInvalidMessages")) {
            p.setDropInvalidMessages(toBoolean(section.get("dropInvalidMessages"), p.isDropInvalidMessages()));
        }
        Map<String, Object> producerConfig = getMap(section, "producerConfig");
        p.setProducerConfig(deepCopyMap(producerConfig));
        return p;
    }

    private static boolean toBoolean(Object v, boolean defaultValue) {
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return defaultValue;
    }

    // Deep copy so config is independent of the parsed YAML tree. Without it, the
    // maps we pass to Pulsar (and store in LoadedPulsarConfig) are the same objects
    // as inside theparse result. If Pulsar or our code later mutates those maps,
    // the original tree would change too—surprising and error-prone. Copying lets
    // us drop the parse result after loading and keeps mutations local.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                out.put(e.getKey(), deepCopyMap((Map<String, Object>) v));
            } else if (v instanceof List) {
                out.put(e.getKey(), deepCopyList((List<?>) v));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> out = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map) {
                out.add(deepCopyMap((Map<String, Object>) item));
            } else if (item instanceof List) {
                out.add(deepCopyList((List<?>) item));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    // Resolves ${VAR} and ${VAR:default} in string values (e.g. authParams). Only recurses into maps.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolvePlaceholders(Map<String, Object> root) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                out.put(e.getKey(), resolvePlaceholders((Map<String, Object>) v));
            } else if (v instanceof String) {
                out.put(e.getKey(), resolvePlaceholdersInString((String) v));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static String resolvePlaceholdersInString(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        if (!m.find()) {
            return value;
        }
        StringBuffer sb = new StringBuffer();
        m.reset();
        while (m.find()) {
            String name = m.group(1);
            String defaultValue = m.group(2);
            String env = System.getenv(name);
            String replacement = env != null ? env : (defaultValue != null ? defaultValue : "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Getter
    @RequiredArgsConstructor
    public static final class LoadedPulsarConfig {
        private final PulsarClientProperties clientProperties;
        private final PulsarAdminProperties adminProperties;
        private final PulsarConsumerProperties consumerProperties;
        private final PulsarProducerProperties producerProperties;
    }
}
