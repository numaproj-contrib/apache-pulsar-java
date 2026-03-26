package io.numaproj.pulsar;

import io.numaproj.pulsar.config.PulsarConfigLoader;
import io.numaproj.pulsar.config.admin.PulsarAdminConfig;
import io.numaproj.pulsar.config.client.PulsarClientConfig;
import io.numaproj.pulsar.config.producer.PulsarProducerConfig;
import io.numaproj.pulsar.consumer.PulsarConsumerManager;
import io.numaproj.pulsar.consumer.PulsarSource;
import io.numaproj.pulsar.producer.PulsarSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Producer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
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

    // Resolves --config=file:... from the pipeline. Accepts file:/path
    private static Path getConfigPath(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.startsWith("--config=file:")) {
                String path = arg.substring("--config=file:".length()).trim();
                if (!path.isEmpty()) {
                    return Paths.get(path);
                }
            }
        }
        throw new IllegalArgumentException(
                "Missing --config=file:/path/to/application.yml. "
                        + "The config file is not read from the working directory by default; pass the path to your YAML (e.g. --config=file:/conf/application.yml in the pipeline).");
    }

    private static void runConsumer(PulsarConfigLoader.LoadedPulsarConfig config) throws Exception {
        config.getConsumerProperties().init();

        PulsarClient pulsarClient = null;
        PulsarAdmin pulsarAdmin = null;
        PulsarConsumerManager consumerManager = null;
        PulsarSource source = null;
        Thread shutdownHook = null;

        try {
            pulsarClient = PulsarClientConfig.create(config.getClientProperties());
            pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
            consumerManager =
                    new PulsarConsumerManager(config.getConsumerProperties(), pulsarClient);
            source = new PulsarSource(consumerManager, pulsarAdmin, config.getConsumerProperties());

            // JVM shutdown: runs when the process exits (e.g. SIGTERM on pod stop, SIGINT, System.exit).
            PulsarSource finalSource = source;
            shutdownHook = new Thread(finalSource::cleanup, "pulsar-source-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            source.startServer();
        } finally {
            // If the server exits normally or startup fails, run the same cleanup path we use on JVM shutdown.
            removeShutdownHook(shutdownHook);
            if (source != null) {
                // Normal path: the source owns the consumer manager and admin client lifecycle.
                source.cleanup();
            } else {
                // Construction may fail partway through; close only the resources that were actually created.
                if (consumerManager != null) {
                    // The source was never created, but the manager can still close its consumers and Pulsar client.
                    consumerManager.cleanup();
                } else {
                    // Earliest failure path: fall back to individually closing whichever low-level clients exist.
                    closePulsarAdmin(pulsarAdmin);
                    closePulsarClient(pulsarClient);
                }
            }
        }
    }

    private static void runProducer(PulsarConfigLoader.LoadedPulsarConfig config) throws Exception {
        config.getProducerProperties().validateConfig();

        PulsarClient pulsarClient = null;
        PulsarSink sink = null;
        PulsarAdmin pulsarAdmin = null;
        Thread shutdownHook = null;

        try {
            pulsarClient = PulsarClientConfig.create(config.getClientProperties());
            pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
            Producer<byte[]> producer = PulsarProducerConfig.create(
                    pulsarClient,
                    config.getProducerProperties(),
                    pulsarAdmin);
            sink = new PulsarSink(producer, pulsarClient, config.getProducerProperties());

            // JVM shutdown: runs when the process exits (e.g. SIGTERM on pod stop, SIGINT, System.exit).
            PulsarSink finalSink = sink;
            PulsarAdmin finalPulsarAdmin = pulsarAdmin;
            shutdownHook = new Thread(() -> {
                finalSink.cleanup();
                closePulsarAdmin(finalPulsarAdmin);
            }, "pulsar-sink-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            sink.startServer();
        } finally {
            // Mirror shutdown-hook cleanup on normal exit and on partial startup failure.
            removeShutdownHook(shutdownHook);
            if (sink != null) {
                sink.cleanup();
            }
            closePulsarAdmin(pulsarAdmin);
            if (sink == null) {
                closePulsarClient(pulsarClient);
            }
        }
    }

    private static void removeShutdownHook(Thread shutdownHook) {
        if (shutdownHook == null) {
            return;
        }

        try {
            // Prevent the hook from firing after we've already cleaned up in the current thread.
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // Once JVM shutdown has started, Runtime disallows unregistering hooks. Typical when SIGTERM
            // arrives (e.g. Kubernetes pod delete) while main is still exiting after awaitTermination().
            log.info(
                    "Skipping shutdown hook unregister: JVM shutdown already in progress. "
                            + "This is expected under process termination; cleanup still runs here or on the hook. "
                            + "Duplicate work is avoided by idempotent cleanup.");
            log.debug("removeShutdownHook: Shutdown in progress (expected)", e);
        }
    }

    private static void closePulsarAdmin(PulsarAdmin pulsarAdmin) {
        if (pulsarAdmin == null) {
            return;
        }

        try {
            pulsarAdmin.close();
        } catch (Exception e) {
            log.warn("Failed to close Pulsar admin during cleanup.", e);
        }
    }

    private static void closePulsarClient(PulsarClient pulsarClient) {
        if (pulsarClient == null) {
            return;
        }

        try {
            pulsarClient.close();
        } catch (Exception e) {
            log.warn("Failed to close Pulsar client during cleanup.", e);
        }
    }
}
