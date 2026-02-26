#!/usr/bin/env python3
# Generic Avro encoder: takes a schema file path and optional record data, writes one Avro binary message to stdout.
# Works with any Avro record schema. Record data can be from a JSON file, stdin, or auto-generated defaults.
#
# Usage: encode_avro_generic.py <schema-file> [data-file]
#   schema-file  Path to schema (Avro JSON or Pulsar format with "type":"AVRO" and "schema" key).
#   data-file    Optional. JSON file with one record matching the schema. If omitted, reads JSON from stdin;
#                if stdin is empty, generates a default record from the schema.

import json
import io
import sys
from pathlib import Path


def load_schema_from_file(path: str):
    """Load Avro schema. Supports raw Avro JSON or Pulsar wrapper {"type":"AVRO","schema":"..."}."""
    with open(path, "r") as f:
        raw = json.load(f)
    if isinstance(raw, dict) and "schema" in raw:
        schema_str = raw["schema"]
        if isinstance(schema_str, str):
            raw = json.loads(schema_str)
        else:
            raw = schema_str
    return raw


def default_for_schema(schema_obj, field_name=None):
    """Build a default value for an Avro schema (for records: dict of field defaults)."""
    if isinstance(schema_obj, dict):
        type_name = schema_obj.get("type")
    elif isinstance(schema_obj, str):
        type_name = schema_obj
    else:
        return None
    if isinstance(schema_obj, dict) and type_name == "record":
        return {
            f["name"]: default_for_schema(f["type"], f["name"])
            for f in schema_obj.get("fields", [])
        }
    if type_name == "string":
        if field_name == "topic":
            return "test-topic"
        return "test-message"
    if type_name in ("int", "long"):
        return 0
    if type_name in ("float", "double"):
        return 0.0
    if type_name == "boolean":
        return False
    if type_name == "null":
        return None
    if type_name == "bytes":
        return b""
    if type_name == "array":
        return []
    if type_name == "map":
        return {}
    if isinstance(type_name, list):
        for t in type_name:
            if t != "null":
                return default_for_schema(t, field_name)
        return None
    return None


def main():
    if len(sys.argv) < 2:
        print("Usage: encode_avro_generic.py <schema-file> [data-file]", file=sys.stderr)
        sys.exit(1)

    schema_path = sys.argv[1]
    data_path = sys.argv[2] if len(sys.argv) > 2 else None

    if not Path(schema_path).is_file():
        print(f"Error: schema file not found: {schema_path}", file=sys.stderr)
        sys.exit(1)

    schema_dict = load_schema_from_file(schema_path)

    try:
        import avro.schema
        import avro.io
    except ImportError:
        print("Error: Python 'avro' package required. Run: pip install avro", file=sys.stderr)
        sys.exit(1)

    schema = avro.schema.parse(json.dumps(schema_dict))

    if data_path:
        with open(data_path, "r") as f:
            record = json.load(f)
    else:
        if not sys.stdin.isatty():
            try:
                line = sys.stdin.readline()
                if line.strip():
                    record = json.loads(line)
                else:
                    record = default_for_schema(schema_dict)
            except json.JSONDecodeError as e:
                print(f"Error: invalid JSON from stdin: {e}", file=sys.stderr)
                sys.exit(1)
        else:
            record = default_for_schema(schema_dict)

    writer = avro.io.DatumWriter(schema)
    buf = io.BytesIO()
    encoder = avro.io.BinaryEncoder(buf)
    writer.write(record, encoder)
    sys.stdout.buffer.write(buf.getvalue())


if __name__ == "__main__":
    main()
