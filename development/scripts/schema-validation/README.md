# Register schema and publish messages

Scripts to register schemas on Pulsar topics and publish test messages. All connection settings come from a `.env` file.

## Prerequisites

- **pulsarctl** – for schema registration (e.g. `brew install pulsarctl`)
- **Python 3** – for encoder scripts used by `publish-messages.sh`
- **curl** – used by the publish script to POST messages

Optional: `pip install avro` for canonical Avro encoding in `avro_encode_name_only.py` (script has a fallback if the library is missing).

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

Sends messages to a topic by running an encoder script once per message and POSTing the binary output to the Pulsar admin REST API.

**Usage:**

```bash
./publish-messages.sh <encoder-script> [topic] [num-messages]
```

| Argument | Required | Description |
|----------|----------|-------------|
| `encoder-script` | Yes | Script that writes one message (binary) to stdout (e.g. `avro_encode_full.py`). Path can be relative to this directory or absolute. |
| `topic` | No | Full topic name. If omitted, uses `PULSAR_TOPIC` from `.env`. |
| `num-messages` | No | Number of messages to send. Default: 15 or `NUM_MESSAGES` from `.env`. |

The encoder is invoked with **no arguments**; it writes one message (binary) to stdout. Message content is hardcoded inside the encoder script.

**Examples:**

```bash
# Default topic and 15 messages
./publish-messages.sh avro_encode_full.py
./publish-messages.sh avro_encode_name_only.py

# Override topic and count
./publish-messages.sh avro_encode_name_only.py persistent://tenant/ns/my-topic 10
```

**Included encoder scripts:**

- **`avro_encode_full.py`** – Encodes a record with `name` and `topic` (matches `schema-test-topic-avro.json`). No arguments; hardcoded body (`test-message`, `test-topic`).
- **`avro_encode_name_only.py`** – Encodes a record with only `name`. No arguments; hardcoded body (`test-message`). Use with the name-only schema, or to send messages that do *not* match the full schema (e.g. to test broker rejection or consumer handling).

---

## Example workflows

**Register full Avro schema and publish valid messages:**

```bash
./register-schema.sh schema-test-topic-avro.json
./publish-messages.sh avro_encode_full.py
```

**Register name-only schema and publish matching messages:**

```bash
./register-schema.sh schema-test-topic-avro-name-only.json
./publish-messages.sh avro_encode_name_only.py
```

**Test schema validation (broker should reject):**

Register the full schema, then publish name-only messages. This should lead to a java io exception when you try to consume from that topic because the messages in the topic do not match the schema.

```bash
./register-schema.sh schema-test-topic-avro.json
./publish-messages.sh avro_encode_name_only.py
# Expect "rejected" for each message
```

---

## Adding your own schema or encoder

- **Schema:** Create a JSON schema file (see existing `schema-*.json` for format) and pass it to `register-schema.sh`.
- **Encoder:** Implement a script that takes no arguments and writes a single binary message to stdout. Message content can be hardcoded. Pass the script path to `publish-messages.sh`.
