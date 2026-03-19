package io.numaproj.pulsar.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Pipeline-shaped YAML files live under src/test/resources:
 * pulsar-config-loader-full-sections.yml (full client/admin/consumer/producer tree),
 * pulsar-config-loader-from-path.yml (loadConfig from filesystem Path).
 * Small edge cases (empty doc, missing key, placeholders, string booleans, deep-copy smoke) stay inline.
 */
public class PulsarConfigLoaderTest {

    /** Name we assume is not set in the test process environment (only used with explicit :default in YAML). */
    private static final String UNLIKELY_ENV = "PULSAR_JAVA_CONFIG_LOADER_TEST_VAR_ZZ99_UNSET_Q";

    // Wraps inline YAML as an input stream for loadConfig.
    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    // Opens a classpath resource from src/test/resources; fails if the file is missing.
    private static InputStream fixtureStream(String classpathResource) {
        InputStream in = PulsarConfigLoaderTest.class.getResourceAsStream("/" + classpathResource);
        assertNotNull("missing test resource: " + classpathResource, in);
        return in;
    }

    // Empty YAML yields empty client/admin maps and disabled consumer/producer flags.
    @Test
    public void load_emptyDocument_emptyClientAdminMaps() {
        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(""));

        assertNotNull(loaded.getClientProperties().getClientConfig());
        assertTrue(loaded.getClientProperties().getClientConfig().isEmpty());
        assertNotNull(loaded.getAdminProperties().getAdminConfig());
        assertTrue(loaded.getAdminProperties().getAdminConfig().isEmpty());
        assertFalse(loaded.getConsumerProperties().isEnabled());
        assertFalse(loaded.getProducerProperties().isEnabled());
    }

    // Document without a top-level pulsar key falls back to empty sections.
    @Test
    public void load_missingPulsarKey_defaultsOnly() {
        String yaml = "other:\n  k: v\n";
        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        assertTrue(loaded.getClientProperties().getClientConfig().isEmpty());
        assertTrue(loaded.getProducerProperties().getProducerConfig().isEmpty());
    }

    // Fixture: all pulsar sections populate maps and boolean flags as expected.
    @Test
    public void load_fullSections_mapsAndFlags() throws IOException {
        PulsarConfigLoader.LoadedPulsarConfig loaded;
        try (InputStream in = fixtureStream("pulsar-config-loader-full-sections.yml")) {
            loaded = PulsarConfigLoader.loadConfig(in);
        }

        assertEquals("pulsar://broker:6650", loaded.getClientProperties().getClientConfig().get("serviceUrl"));
        assertEquals("http://broker:8080", loaded.getAdminProperties().getAdminConfig().get("serviceUrl"));
        assertTrue(loaded.getConsumerProperties().isEnabled());
        assertFalse(loaded.getConsumerProperties().isUseAutoConsumeSchema());
        assertEquals("my-topic", loaded.getConsumerProperties().getConsumerConfig().get("topicNames"));
        assertFalse(loaded.getProducerProperties().isEnabled());
        assertTrue(loaded.getProducerProperties().isUseAutoProduceSchema());
        assertFalse(loaded.getProducerProperties().isDropInvalidMessages());
        assertEquals("persistent://tn/ns/t1", loaded.getProducerProperties().getProducerConfig().get("topicName"));
    }

    // Quoted "true"/"false" strings in YAML coerce to boolean flags on consumer/producer.
    @Test
    public void load_booleanFlags_acceptStringTrueFalse() {
        String yaml = ""
                + "pulsar:\n"
                + "  consumer:\n"
                + "    enabled: \"true\"\n"
                + "    useAutoConsumeSchema: \"false\"\n"
                + "  producer:\n"
                + "    enabled: \"false\"\n"
                + "    useAutoProduceSchema: \"true\"\n"
                + "    dropInvalidMessages: \"true\"\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        assertTrue(loaded.getConsumerProperties().isEnabled());
        assertFalse(loaded.getConsumerProperties().isUseAutoConsumeSchema());
        assertFalse(loaded.getProducerProperties().isEnabled());
        assertTrue(loaded.getProducerProperties().isUseAutoProduceSchema());
        assertTrue(loaded.getProducerProperties().isDropInvalidMessages());
    }

    // Unset env var with :default in placeholder expands to the default segment only.
    @Test
    public void load_placeholder_usesDefaultWhenEnvUnset() {
        assertTrue("Test requires env var to be unset: " + UNLIKELY_ENV, System.getenv(UNLIKELY_ENV) == null);

        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      token: \"prefix-${" + UNLIKELY_ENV + ":fallback}-suffix\"\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        assertEquals("prefix-fallback-suffix",
                loaded.getClientProperties().getClientConfig().get("token"));
    }

    // Unset env var without default removes the placeholder, leaving adjacent text intact.
    @Test
    public void load_placeholder_missingEnvNoDefault_becomesEmpty() {
        assertTrue("Test requires env var to be unset: " + UNLIKELY_ENV, System.getenv(UNLIKELY_ENV) == null);

        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      token: \"x${" + UNLIKELY_ENV + "}y\"\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        assertEquals("xy", loaded.getClientProperties().getClientConfig().get("token"));
    }

    // Several ${VAR:default} tokens in one string each resolve when env is unset.
    @Test
    public void load_placeholder_multipleInOneString() {
        assertTrue("Test requires env var to be unset: " + UNLIKELY_ENV, System.getenv(UNLIKELY_ENV) == null);

        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      authParams: \"a=${" + UNLIKELY_ENV + ":1} b=${" + UNLIKELY_ENV + ":2}\"\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        assertEquals("a=1 b=2", loaded.getClientProperties().getClientConfig().get("authParams"));
    }

    // Placeholder resolution walks nested maps under clientConfig, not only top-level keys.
    @Test
    public void load_placeholders_recursiveInNestedMaps() {
        assertTrue("Test requires env var to be unset: " + UNLIKELY_ENV, System.getenv(UNLIKELY_ENV) == null);

        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      outer:\n"
                + "        inner: \"v${" + UNLIKELY_ENV + ":ok}\"\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));

        @SuppressWarnings("unchecked")
        Map<String, Object> outer = (Map<String, Object>) loaded.getClientProperties().getClientConfig().get("outer");
        assertNotNull(outer);
        assertEquals("vok", outer.get("inner"));
    }

    // Mutating a prior load's client map does not affect a later reload (deep copy).
    @Test
    public void load_deepCopy_clientConfigIndependentOfReload() {
        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      k: v\n";

        PulsarConfigLoader.LoadedPulsarConfig a = PulsarConfigLoader.loadConfig(stream(yaml));
        a.getClientProperties().getClientConfig().put("mutated", true);

        PulsarConfigLoader.LoadedPulsarConfig b = PulsarConfigLoader.loadConfig(stream(yaml));

        assertFalse(b.getClientProperties().getClientConfig().containsKey("mutated"));
        assertEquals("v", b.getClientProperties().getClientConfig().get("k"));
    }

    // Two loads produce distinct nested map instances for the same YAML path.
    @Test
    public void load_deepCopy_nestedMapIsNewInstance() {
        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      nested:\n"
                + "        x: 1\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) loaded.getClientProperties().getClientConfig().get("nested");

        PulsarConfigLoader.LoadedPulsarConfig loaded2 = PulsarConfigLoader.loadConfig(stream(yaml));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested2 = (Map<String, Object>) loaded2.getClientProperties().getClientConfig().get("nested");

        assertNotSame(nested, nested2);
    }

    // Mutating a loaded list does not change the list from a fresh load (copy per load).
    @Test
    public void load_deepCopy_listElementsCopied() {
        String yaml = ""
                + "pulsar:\n"
                + "  client:\n"
                + "    clientConfig:\n"
                + "      list:\n"
                + "        - a\n"
                + "        - b\n";

        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(stream(yaml));
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) loaded.getClientProperties().getClientConfig().get("list");
        list.add("c");

        PulsarConfigLoader.LoadedPulsarConfig loaded2 = PulsarConfigLoader.loadConfig(stream(yaml));
        @SuppressWarnings("unchecked")
        List<Object> list2 = (List<Object>) loaded2.getClientProperties().getClientConfig().get("list");

        assertEquals(2, list2.size());
        assertEquals(List.of("a", "b"), list2);
    }

    // loadConfig(Path) reads a real file; uses the from-path fixture on the classpath.
    @Test
    public void load_fromPath_readsFile() throws IOException, URISyntaxException {
        URL url = getClass().getResource("/pulsar-config-loader-from-path.yml");
        assertNotNull(url);
        Path path = Path.of(url.toURI());
        PulsarConfigLoader.LoadedPulsarConfig loaded = PulsarConfigLoader.loadConfig(path);
        assertEquals("pulsar://file-test:6650",
                loaded.getClientProperties().getClientConfig().get("serviceUrl"));
    }
}
