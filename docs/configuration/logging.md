# Logging

There are two independent settings: **format** (how logs look) and **level** (how much gets logged). They can be combined however you like.

| Setting | What it controls | Environment variable |
|---|---|---|
| **Format** | JSON vs plain text output | `NUMAFLOW_DEBUG` |
| **Level** | How many messages are printed | `LOGGING_LEVEL_ROOT` or `LOGGING_LEVEL_IO_NUMAPROJ_PULSAR` |

## Log Format (`NUMAFLOW_DEBUG`)

Controls **how** logs look — does not affect which messages are shown.

By default, logs are output as **JSON** (for log aggregators like Loki, Datadog, ELK). To switch to **plain text** for local development, set:

```yaml
env:
  - name: NUMAFLOW_DEBUG
    value: "true"
```

**JSON format** (default):

```json
{"@timestamp":"2026-04-22T13:22:46.448Z","@version":"1","message":"Producer connected; schema initialized: type=BYTES, name=Bytes, schema=","logger_name":"io.numaproj.pulsar.config.producer.PulsarProducerConfig","thread_name":"main","level":"INFO"}
```

**Plain text format** (`NUMAFLOW_DEBUG=true`):

```
2026-04-22 13:22:46.448  INFO --- [main] i.n.p.config.producer.PulsarProducerConfig : Producer connected; schema initialized: type=BYTES, name=Bytes, schema=
```

## Log Level (`LOGGING_LEVEL_*`)

Controls **which** messages are printed — does not affect how they look.

| Level | What gets logged | Use case |
|---|---|---|
| `TRACE` | Everything, including very detailed internal steps | Deep debugging |
| `DEBUG` | Per-message processing details | Debugging message flow |
| `INFO` | Normal operational events (startup, connections, config) | **Default** |
| `WARN` | Potential issues that don't stop the pipeline | Monitoring |
| `ERROR` | Failures only | Quiet logs, production |
| `OFF` | Nothing | Silence all logs |

Setting a level **drops all messages below it**. For example, `WARN` means you only see `WARN` and `ERROR` — all `INFO`, `DEBUG`, and `TRACE` messages are discarded.

```yaml
env:
  # Set root level (affects all packages)
  - name: LOGGING_LEVEL_ROOT
    value: "WARN"
  # Set level for io.numaproj.pulsar package
  - name: LOGGING_LEVEL_IO_NUMAPROJ_PULSAR
    value: "DEBUG"
```

You can set different levels for different packages. In the example above, the Pulsar library logs at `DEBUG` while everything else is `WARN`.

## Example: Enabling Debug Logs in a Pipeline

In your pipeline or MonoVertex YAML, add the environment variables to the container spec:

```yaml
source:
  udsource:
    container:
      image: apache-pulsar-java:v0.3.0
      args: ["--config=file:/conf/application.yml"]
      env:
        - name: LOGGING_LEVEL_IO_NUMAPROJ_PULSAR
          value: "DEBUG"
        - name: NUMAFLOW_DEBUG
          value: "true"
```
