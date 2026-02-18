package io.numaproj.pulsar.consumer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.pulsar.common.schema.SchemaType;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that GenericRecordToBytes.toBytes() correctly serializes Avro-backed
 * Pulsar GenericRecords to Avro binary bytes (round-trip).
 */
public class GenericRecordToBytesTest {

    private static final String SCHEMA_JSON = ""
            + "{\"type\":\"record\",\"name\":\"TestMessage\",\"fields\":["
            + "{\"name\":\"name\",\"type\":\"string\"},"
            + "{\"name\":\"topic\",\"type\":\"string\"}"
            + "]}";

    @Test
    public void toAvroBytes_roundTrip() throws IOException {
        Schema schema = new Schema.Parser().parse(SCHEMA_JSON);
        GenericData.Record avroRecord = new GenericData.Record(schema);
        avroRecord.put("name", "hello");
        avroRecord.put("topic", "world");

        org.apache.pulsar.client.api.schema.GenericRecord pulsarRecord =
                mock(org.apache.pulsar.client.api.schema.GenericRecord.class);
        when(pulsarRecord.getSchemaType()).thenReturn(SchemaType.AVRO);
        when(pulsarRecord.getNativeObject()).thenReturn(avroRecord);

        byte[] bytes = GenericRecordToBytes.toBytes(pulsarRecord);

        assertNotNull(bytes);
        assertTrue("should produce non-empty Avro binary", bytes.length > 0);

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        GenericRecord decoded = reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));

        assertEquals("hello", decoded.get("name").toString());
        assertEquals("world", decoded.get("topic").toString());
    }

    @Test
    public void toBytes_null_returnsEmptyArray() throws IOException {
        byte[] bytes = GenericRecordToBytes.toBytes(null);
        assertNotNull(bytes);
        assertEquals(0, bytes.length);
    }
}
