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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        CountDownLatch serverStopped = new CountDownLatch(1);

        try {
            pulsarClient = PulsarClientConfig.create(config.getClientProperties());
            pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
            consumerManager =
                    new PulsarConsumerManager(config.getConsumerProperties(), pulsarClient);
            source = new PulsarSource(consumerManager, pulsarAdmin, config.getConsumerProperties());

            // On SIGTERM the SDK's hook drains the gRPC server, but the JVM halts before the
            // finally block can close Pulsar resources. Putting a shutdown hook here waits for the drain to finish
            // (via the latch) then closes resources, preventing the JVM from terminating/halting until the resources are closed 
            PulsarSource hookSource = source;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Timer starts when JVM enters shutdown mode.
                    // Both hooks run concurrently — this hook blocks on the latch, waiting for the SDK's hook to drain the gRPC server 
                    // (which takes up to 30 seconds). The extra 5 seconds accounts for delays associated with
                    // main thread reaching countDown() after awaitTermination() returns.
                    serverStopped.await(35, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                hookSource.cleanup();
                log.info("Consumer cleanup finished (shutdown hook).");
            }, "pulsar-source-shutdown"));

            // Blocks until gRPC server terminates (SIGTERM causes graceful drain, letting
            // in-flight ack() calls complete before returning).
            source.startServer();
        } finally {
            serverStopped.countDown();

            // When source is fully constructed the shutdown hook is registered and owns cleanup
            // for every exit path (SIGTERM and normal JVM exit both trigger hooks).
            // only clean up here for partial-construction failures where the hook was never registered:
            //   - consumerManager != null: source construction failed.
            //     consumerManager owns consumers + client; admin must be closed separately.
            //   - both null: earliest failure. Close client and admin individually.
            if (source == null) {
                if (consumerManager != null) {
                    consumerManager.cleanup();
                } else {
                    closePulsarClient(pulsarClient);
                }
                closePulsarAdmin(pulsarAdmin);
                log.info("Consumer cleanup finished.");
            }
        }
    }

    private static void runProducer(PulsarConfigLoader.LoadedPulsarConfig config) throws Exception {
        config.getProducerProperties().validateConfig();

        PulsarClient pulsarClient = null;
        PulsarSink sink = null;
        PulsarAdmin pulsarAdmin = null;

        CountDownLatch serverStopped = new CountDownLatch(1);

        try {
            pulsarClient = PulsarClientConfig.create(config.getClientProperties());
            pulsarAdmin = PulsarAdminConfig.create(config.getAdminProperties());
            Producer<byte[]> producer = PulsarProducerConfig.create(
                    pulsarClient,
                    config.getProducerProperties(),
                    pulsarAdmin);

            // Admin is only needed for topic validation during producer creation.
            // Close it immediately so we don't hold an idle connection for the process lifetime.
            closePulsarAdmin(pulsarAdmin);
            pulsarAdmin = null;

            sink = new PulsarSink(producer, pulsarClient, config.getProducerProperties());

            PulsarSink hookSink = sink;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    serverStopped.await(35, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                hookSink.cleanup();
                log.info("Producer cleanup finished (shutdown hook).");
            }, "pulsar-sink-shutdown"));

            // Blocks until gRPC server terminates.
            sink.startServer();
        } finally {
            serverStopped.countDown();

            // When sink is fully constructed the shutdown hook owns cleanup for every exit path.
            // only clean up here for partial-construction failures where the hook was never
            // registered. 
            if (sink == null) {
                closePulsarClient(pulsarClient);
                closePulsarAdmin(pulsarAdmin);
                log.info("Producer cleanup finished.");
            }
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
