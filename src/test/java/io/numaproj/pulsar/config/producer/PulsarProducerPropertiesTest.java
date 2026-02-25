package io.numaproj.pulsar.config.producer;

import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PulsarProducerPropertiesTest {

    @Test
    public void validate_useAutoProduceSchemaFalse_dropInvalidMessagesTrue_throwsException() {
        PulsarProducerProperties properties = new PulsarProducerProperties();
        properties.setUseAutoProduceSchema(false);
        properties.setDropInvalidMessages(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                properties::validateConfig);

        assertTrue("Message should describe invalid combination",
                exception.getMessage().contains("useAutoProduceSchema=false and dropInvalidMessages=true"));
        assertTrue("Message should mention dropInvalidMessages only applies with auto-produce",
                exception.getMessage().contains("dropInvalidMessages only applies when useAutoProduceSchema is true"));
    }

    @Test
    public void validate_useAutoProduceSchemaTrue_dropInvalidMessagesTrue_doesNotThrow() {
        PulsarProducerProperties properties = new PulsarProducerProperties();
        properties.setUseAutoProduceSchema(true);
        properties.setDropInvalidMessages(true);
        properties.validateConfig();
    }

    @Test
    public void validate_useAutoProduceSchemaFalse_dropInvalidMessagesFalse_doesNotThrow() {
        PulsarProducerProperties properties = new PulsarProducerProperties();
        properties.setUseAutoProduceSchema(false);
        properties.setDropInvalidMessages(false);
        properties.validateConfig();
    }
}
