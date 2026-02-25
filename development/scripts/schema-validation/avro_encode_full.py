#!/usr/bin/env python3
# Encodes an Avro record with "name" and "topic" to match TestMessage schema on test-topic.
# No arguments; message body is hardcoded.

import sys


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


def main():
    name = "test-message"
    topic = "test-topic"
    # Record with two string fields in schema order: name, then topic.
    payload = encode_avro_string(name) + encode_avro_string(topic)
    sys.stdout.buffer.write(payload)


if __name__ == "__main__":
    main()
