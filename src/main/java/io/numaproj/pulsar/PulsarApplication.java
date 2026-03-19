package io.numaproj.pulsar;

import io.numaproj.pulsar.config.PulsarConfigLoader;
import io.numaproj.pulsar.config.admin.PulsarAdminConfig;
import io.numaproj.pulsar.config.client.PulsarClientConfig;
import io.numaproj.pulsar.config.producer.PulsarProducerConfig;
import io.numaproj.pulsar.consumer.PulsarConsumerManager;
import io.numaproj.pulsar.consumer.PulsarSource;
import io.numaproj.pulsar.producer.PulsarSink;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Producer;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PulsarApplication {
    public static void main(String[] args) throws Exception {
        Path configPath = getConfigPath(args);
        PulsarConfigLoader.LoadedPulsarConfig config = PulsarConfigLoader.loadConfig(configPath);

        if (config.getConsumerProperties().isEnabled()) {
            runConsumer(config);
        } else if (config.getProducerProperties().isEnabled()) {
            runProducer(config);
        } else {
            throw new IllegalStateException(
                    "Neither consumer nor producer is enabled. Set pulsar.consumer.enabled or pulsar.producer.enabled to true.");
        }
    }

    // Resolves --config=file:... from the pipeline. Accepts file:/path, file:///path, and tolerates an extra colon
    // after "file:" (file::/path) which would otherwise become the invalid path ":/path".
    private static Path getConfigPath(String[] args) {
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--config=")) {
                continue;
            }
            String value = arg.substring("--config=".length()).trim();
            if (!value.startsWith("file:")) {
                continue;
            }
            try {
                return Paths.get(URI.create(value));
            } catch (IllegalArgumentException ignored) {
                // URI not usable for Paths.get (e.g. opaque file::/path)
            }
            String remainder = value.substring("file:".length()).replaceFirst("^:+", "");
            if (!remainder.isEmpty()) {
                return Paths.get(remainder);
            }
        }
        throw new IllegalArgumentException(
                "Missing --config=file:/path/to/application.yml. "
                        + "The config file is not read from the working directory by default; pass the path to your YAML (e.g. --config=file:/conf/application.yml in the pipeline).");
    }

    private static void runConsumer(PulsarConfigLoader.LoadedPulsarConfig config) throws Exception {
        config.getConsumerProperties().init();

        PulsarClient pulsarClient = PulsarClientConfig.create(config.getClientProperties());
        org.apache.pulsar.client.admin.PulsarAdmin pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
        PulsarConsumerManager consumerManager = new PulsarConsumerManager(config.getConsumerProperties(), pulsarClient);
        PulsarSource source = new PulsarSource(consumerManager, pulsarAdmin, config.getConsumerProperties());

        // JVM shutdown: runs when the process exits (e.g. SIGTERM on pod stop, SIGINT, System.exit). Not triggered by
        // Numaflow alone except as part of stopping the process; does not run on kill -9 or hard crash.
        Runtime.getRuntime().addShutdownHook(new Thread(consumerManager::cleanup));
        source.startServer();
    }

    private static void runProducer(PulsarConfigLoader.LoadedPulsarConfig config) throws Exception {
        config.getProducerProperties().validateConfig();

        PulsarClient pulsarClient = PulsarClientConfig.create(config.getClientProperties());
        org.apache.pulsar.client.admin.PulsarAdmin pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
        Producer<byte[]> producer = PulsarProducerConfig.create(pulsarClient, config.getProducerProperties(),
                pulsarAdmin);
        PulsarSink sink = new PulsarSink(producer, pulsarClient, config.getProducerProperties());

        // JVM shutdown: runs when the process exits (e.g. SIGTERM on pod stop, SIGINT, System.exit). Not triggered by
        // Numaflow alone except as part of stopping the process; does not run on kill -9 or hard crash.
        Runtime.getRuntime().addShutdownHook(new Thread(sink::cleanup));
        sink.startServer();
    }
}
