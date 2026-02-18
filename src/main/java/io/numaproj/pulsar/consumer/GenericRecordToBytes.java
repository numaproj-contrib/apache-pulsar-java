package io.numaproj.pulsar.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.common.schema.SchemaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Converts a Pulsar {@link GenericRecord} (from Schema.AUTO_CONSUME) to byte[] for downstream
 * use. Only AVRO schema is supported.
 */
@Slf4j
public final class GenericRecordToBytes {

    private GenericRecordToBytes() {}

    /**
     * Serializes the given GenericRecord to bytes. Only AVRO schema is supported.
     *
     * @param record the record from a Pulsar message (AUTO_CONSUME)
     * @return serialized bytes, never null
     * @throws IOException if serialization fails
     * @throws UnsupportedOperationException if schema type is not AVRO
     */
    public static byte[] toBytes(GenericRecord record) throws IOException {
        if (record == null) {
            return new byte[0];
        }
        SchemaType schemaType = record.getSchemaType();
        if (schemaType != SchemaType.AVRO) {
            throw new UnsupportedOperationException(
                    "Only AVRO schema is supported; schemaType=" + schemaType);
        }
        return toAvroBytes(record);
    }

    private static byte[] toAvroBytes(GenericRecord record) throws IOException {
        Object nativeObj = record.getNativeObject();
        if (!(nativeObj instanceof org.apache.avro.generic.GenericRecord avroRecord)) {
            throw new IllegalArgumentException(
                    "AVRO schema but native object is not Avro GenericRecord: "
                            + (nativeObj == null ? "null" : nativeObj.getClass().getName()));
        }
        Schema schema = avroRecord.getSchema();
        GenericDatumWriter<org.apache.avro.generic.GenericRecord> writer = new GenericDatumWriter<>(schema);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(avroRecord, encoder);
        encoder.flush();
        return out.toByteArray();
    }
}
