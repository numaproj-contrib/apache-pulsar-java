package io.numaproj.pulsar.config.client;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

public class PulsarClientConfigTest {

    @Mock
    private PulsarClientProperties mockClientProperties;

    private PulsarClientConfig pulsarClientConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        pulsarClientConfig = new PulsarClientConfig();
    }

    // Test to create PulsarClient bean with valid configuration properties
    @Test
    public void pulsarClient_validConfig() {
        Map<String, Object> config = new HashMap<>();
        // URL must include the protocol (pulsar:// or pulsar+ssl://)
        config.put("serviceUrl", "pulsar://test:1234");
        when(mockClientProperties.getClientConfig()).thenReturn(config);
        try {
            PulsarClient client = pulsarClientConfig.pulsarClient(mockClientProperties);
            assertNotNull(client);
            verify(mockClientProperties).getClientConfig();

        } catch (PulsarClientException e) { // PulsarClientException could be thrown by PulsarClient.builder()
            fail("Exception should not have been thrown: " + e.getMessage());
        }

    }

}