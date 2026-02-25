#!/usr/bin/env python3
# Encodes a single Avro record with only "name" (no "topic") to Avro binary.
# No arguments; message body is hardcoded. Use to send valid Avro that doesn't match full schema.
# Output: Avro binary to stdout. Prefers 'avro' library; falls back to hand-rolled encoding.

import io
import sys

# Hardcoded message body
DEFAULT_NAME = "test-message"

# Schema: one string field "name" only (topic schema has name + topic).
NAME_ONLY_SCHEMA = """{
  "type": "record",
  "name": "NameOnlyMessage",
  "fields": [{"name": "name", "type": "string"}]
}"""


def encode_with_avro_lib(name: str) -> bytes:
    """Use standard avro library so output is clearly a valid Avro record (wrong schema)."""
    import avro.io
    import avro.schema
    schema = avro.schema.parse(NAME_ONLY_SCHEMA)
    writer = avro.io.DatumWriter(schema)
    buf = io.BytesIO()
    encoder = avro.io.BinaryEncoder(buf)
    writer.write({"name": name}, encoder)
    return buf.getvalue()


def encode_varint(n):
    """Encode int as Avro variable-length zigzag long."""
    n = (n << 1) ^ (n >> 63) if n >= 0 else ((n << 1) ^ (~n >> 63))
    out = []
    while n > 0x7f:
        out.append((n & 0x7f) | 0x80)
        n >>= 7
    out.append(n & 0x7f)
    return bytes(out)


def encode_avro_string(s):
    """Avro binary encoding for a string: length (long) + UTF-8 bytes."""
    b = s.encode("utf-8")
    return encode_varint(len(b)) + b


def encode_fallback(name: str) -> bytes:
    """Hand-rolled single-field record (same on-the-wire as avro lib for this schema)."""
    return encode_avro_string(name)


def main():
    try:
        payload = encode_with_avro_lib(DEFAULT_NAME)
    except Exception:
        payload = encode_fallback(DEFAULT_NAME)
    sys.stdout.buffer.write(payload)


if __name__ == "__main__":
    main()
