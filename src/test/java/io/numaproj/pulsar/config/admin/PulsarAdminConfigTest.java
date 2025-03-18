package io.numaproj.pulsar.config.admin;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

public class PulsarAdminConfigTest {

    @Mock
    private PulsarAdminProperties mockAdminProperties;

    private PulsarAdminConfig pulsarAdminConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        pulsarAdminConfig = new PulsarAdminConfig();
    }

    // Test to create PulsarAdmin bean with valid configuration properties
    @Test
    public void pulsarAdmin_validConfig() {
        Map<String, Object> config = new HashMap<>();
        // URL must include the protocol (http://)
        config.put("serviceUrl", "http://test:1234");
        when(mockAdminProperties.getAdminConfig()).thenReturn(config);

        try {
            PulsarAdmin Admin = pulsarAdminConfig.pulsarAdmin(mockAdminProperties);
            assertNotNull(Admin);
            verify(mockAdminProperties).getAdminConfig();
        } catch (PulsarClientException e) { // PulsarClientException could be thrown by PulsarAdmin.builder()
            fail("Exception should not have been thrown: " + e.getMessage());
        }
    }
}