# Register schema and publish messages

Scripts to register schemas on Pulsar topics and publish test messages for schema validation testing. All connection settings come from a `.env` file.

## Prerequisites

- **pulsarctl** – for schema registration (e.g. `brew install pulsarctl`)
- **Python 3** – for the generic Avro encoder used by `publish-messages.sh`
- **curl** – used by the publish script to POST messages
- **pip install avro** – required by `encode_avro_generic.py` for encoding

## Setup

1. Copy the example env file and fill in your Pulsar details:

   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and set (use `KEY=value` with no spaces around `=`):

   | Variable | Required | Description |
   |----------|----------|-------------|
   | `PULSAR_ADMIN_URL` | Yes | Pulsar admin service URL (e.g. StreamNative Cloud or your cluster). |
   | `PULSAR_TOPIC` | Yes | Full topic name: `persistent://tenant/namespace/topic-name`. |
   | `PULSAR_AUTH_TOKEN` | Yes | JWT for admin and REST publish. |
   | `NUM_MESSAGES` | No | Default number of messages for `publish-messages.sh` (default: 15). |

---

## Register a schema

**Script:** `register-schema.sh`

Uploads a schema to a topic and turns on schema validation for that topic’s namespace.

**Usage:**

```bash
./register-schema.sh <schema-file> [topic]
```

| Argument | Required | Description |
|----------|----------|-------------|
| `schema-file` | Yes | Path to the schema JSON file (relative to this directory or absolute). |
| `topic` | No | Full topic name. If omitted, uses `PULSAR_TOPIC` from `.env`. |

**Examples:**

```bash
# Use topic from .env
./register-schema.sh schema-test-topic.json
./register-schema.sh schema-test-topic-avro.json

# Override topic
./register-schema.sh schema-test-topic-avro.json persistent://other-tenant/ns/my-topic
```

**Included schema files:**

- `schema-test-topic.json` – JSON schema (TestMessage: name, topic)
- `schema-test-topic-avro.json` – AVRO schema (TestMessage: name, topic)
- `schema-test-topic-avro-name-only.json` – AVRO schema (single field: name)

---

## Publish messages

**Script:** `publish-messages.sh`

Sends messages to a topic by running a **generic Avro encoder** once per message (using the schema file you pass) and POSTing the binary output to the Pulsar admin REST API.

**Why encoding?** When a topic has an Avro schema, the broker expects the message body to be **Avro binary** (the same format a real Avro producer would send). You cannot POST raw JSON or plain text and have it accepted as a valid Avro message. The generic encoder produces that binary from your schema (and optional record data) so the curl request body is in the correct format.

**Usage:**

```bash
./publish-messages.sh <schema-file> [topic] [num-messages] [data-file]
```

| Argument | Required | Description |
|----------|----------|-------------|
| `schema-file` | Yes | Path to the Avro schema (JSON or Pulsar format). Same files you use with `register-schema.sh`. Relative to this directory or absolute. |
| `topic` | No | Full topic name. If omitted, uses `PULSAR_TOPIC` from `.env`. |
| `num-messages` | No | Number of messages to send. Default: 15 or `NUM_MESSAGES` from `.env`. |
| `data-file` | No | JSON file with one record matching the schema. If omitted, the encoder generates default values from the schema (e.g. empty strings, zeros). |

**Examples:**

```bash
# Default topic and 15 messages (encoder uses default record from schema)
./publish-messages.sh schema-test-topic-avro.json
./publish-messages.sh schema-test-topic-avro-name-only.json

# Override topic and count
./publish-messages.sh schema-test-topic-avro-name-only.json persistent://tenant/ns/my-topic 10

# Use a custom JSON record for each message
./publish-messages.sh schema-test-topic-avro.json persistent://tenant/ns/my-topic 5 my-record.json
```

**Generic encoder:** `encode_avro_generic.py`

- Works with **any** Avro record schema. You pass the schema file path (and optionally a JSON record).
- Accepts raw Avro schema JSON or Pulsar format (`{"type":"AVRO","schema":"..."}`).
- Without a data file: builds a default record from the schema (strings → `""`, numbers → `0`, etc.) so you can publish without writing record JSON.

---

## Example workflows

**Register full Avro schema and publish valid messages:**

```bash
./register-schema.sh schema-test-topic-avro.json
./publish-messages.sh schema-test-topic-avro.json
```

**Register name-only schema and publish matching messages:**

```bash
./register-schema.sh schema-test-topic-avro-name-only.json
./publish-messages.sh schema-test-topic-avro-name-only.json
```

**Test schema validation :**

Register the full schema, then publish name-only messages (wrong schema). This should lead to a java io exception when you try to consume from that topic because the messages in the topic do not match the schema.
```bash
./register-schema.sh schema-test-topic-avro.json
./publish-messages.sh schema-test-topic-avro-name-only.json
# Expect "rejected" for each message
```

---

## Adding your own schema

- Create a schema file (see existing `schema-*.json` for format) and pass it to `register-schema.sh`.
- Use the **same schema file** with `publish-messages.sh` to publish messages. The generic encoder works with any Avro record schema. Optionally pass a JSON data file so each message uses your record instead of default values.
