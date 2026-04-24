package io.numaproj.pulsar.config.producer;

import io.numaproj.pulsar.config.EnvLookup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.schema.SchemaInfo;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a Pulsar Producer from configuration.
 */
@Slf4j
public final class PulsarProducerConfig {

    private PulsarProducerConfig() {
    }

    /**
     * Creates a new producer using the system environment for pod-name lookup.
     *
     * @param pulsarClient             the Pulsar client
     * @param pulsarProducerProperties parsed pulsar.producer config
     * @param pulsarAdmin              admin client used to verify topic existence
     * @return the configured producer
     * @throws Exception if validation fails or the producer cannot be built
     */
    public static Producer<byte[]> create(PulsarClient pulsarClient,
            PulsarProducerProperties pulsarProducerProperties,
            PulsarAdmin pulsarAdmin) throws Exception {
        return create(pulsarClient, pulsarProducerProperties, pulsarAdmin, EnvLookup.system());
    }

    /**
     * Creates a new producer using the given environment lookup.
     *
     * @param pulsarClient             the Pulsar client
     * @param pulsarProducerProperties parsed pulsar.producer config
     * @param pulsarAdmin              admin client used to verify topic existence
     * @param envLookup                resolver for the NUMAFLOW_POD environment variable
     * @return the configured producer
     * @throws Exception if validation fails or the producer cannot be built
     */
    public static Producer<byte[]> create(PulsarClient pulsarClient,
            PulsarProducerProperties pulsarProducerProperties,
            PulsarAdmin pulsarAdmin,
            EnvLookup envLookup) throws Exception {
        String podName = Optional.ofNullable(envLookup.get("NUMAFLOW_POD"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> "pod-" + UUID.randomUUID());
        String producerNameKey = "producerName";

        pulsarProducerProperties.validateConfig();

        Map<String, Object> producerConfig = pulsarProducerProperties.getProducerConfig();
        if (producerConfig.containsKey(producerNameKey)) {
            log.warn("User configured a 'producerName' in the config, but this can cause errors if multiple pods spin "
                    + "up with the same name. Overriding with '{}'", podName);
        }
        producerConfig.put(producerNameKey, podName);

        // Validate that the topic configured in the producer config exists in the Pulsar cluster
        String topicName = (String) producerConfig.get("topicName");
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic name must be configured in producer config");
        }

        validateTopicExists(pulsarAdmin, topicName);

        final Schema<byte[]> schema;
        if (pulsarProducerProperties.isUseAutoProduceSchema()) {
            schema = Schema.AUTO_PRODUCE_BYTES();
        } else {
            schema = Schema.BYTES;
            log.info("Producer using Schema.BYTES: no broker-side schema validation.");
        }

        Producer<byte[]> producer = pulsarClient.newProducer(schema)
                .loadConf(producerConfig)
                .create();

        SchemaInfo schemaInfo = schema.getSchemaInfo();
        log.info("Producer connected; schema initialized: type={}, name={}, schema={}",
                schemaInfo.getType(), schemaInfo.getName(),
                schemaInfo.getSchema() != null ? new String(schemaInfo.getSchema(), StandardCharsets.UTF_8) : "null");

        return producer;
    }

    private static void validateTopicExists(PulsarAdmin pulsarAdmin, String topicName) throws PulsarAdminException {
        // Extract namespace from topic name: (persistent|non-persistent)://tenant/namespace/topic
        // Split gives: ["persistent:", "", "tenant", "namespace", "topic", ...]
        // Note: Topic names can contain slashes (for backward compatibility), so parts.length can be > 5
        String[] parts = topicName.split("/");
        if (parts.length < 5) {
            throw new IllegalArgumentException(
                "Invalid topic name format: " + topicName +
                ". Expected format: (persistent|non-persistent)://tenant/namespace/topic"
            );
        }
        String namespace = parts[2] + "/" + parts[3];

        // List all topics in the namespace - works for both partitioned and non-partitioned
        java.util.List<String> topics = pulsarAdmin.topics().getList(namespace);

        // Check if topic exists (exact match for non-partitioned, or starts with topic-partition- for partitioned)
        boolean topicExists = topics.stream()
                .anyMatch(t -> t.equals(topicName) || t.startsWith(topicName + "-partition-"));

        if (topicExists) {
            return;
        }

        String errorMsg = String.format("Topic '%s' does not exist. Please create the topic before starting the producer.", topicName);
        throw new IllegalStateException(errorMsg);
    }
}
