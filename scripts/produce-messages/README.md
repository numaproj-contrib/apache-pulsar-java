# Produce messages to StreamNative Pulsar

Script to send **distinguishable** messages to one or more Pulsar topics on a StreamNative-hosted cluster (e.g. for testing the consumer pipeline). By default it publishes to `test-topic` and `test-topic-2`; set `TOPICS` to use different topics.

## Prerequisites

- **StreamNative cluster**: HTTP service URL and an API key (or token) with produce permission on the tenant/namespace. The service URL and apikey is the same as the one used in the producer/consumer config map.
- **Bash** and **curl**.

## Configuration

Create a `.env` file in this directory (or set the variables in your shell). 

```bash
# StreamNative HTTP service URL (from Cluster Dashboard → Details → Access Points)
export PULSAR_REST_URL="https://pc-xxxx....streamnative.aws.snio.cloud"

# API key (from your consumer/producer secret or StreamNative Console)
export PULSAR_AUTH_TOKEN="<your-token>"

# Optional: tenant and namespace (defaults below match typical consumer pipelines)
export PULSAR_TENANT="demo"
export PULSAR_NAMESPACE="dev"

# Optional: comma-separated topic names (default: test-topic,test-topic-2)
export TOPICS="test-topic,test-topic-2"
```

## Usage

From the **apache-pulsar-java** repo root or from this directory:

```bash
cd scripts/produce-messages
chmod +x produce-messages.sh
./produce-messages.sh
```

Optional: send more messages per topic (default is 5), or use different topics:

```bash
COUNT=10 ./produce-messages.sh
TOPICS="topic-a,topic-b,topic-c" ./produce-messages.sh
```

## Message format (distinguishable by topic)

Each message body is `[<topic-name>] Message <i> at <timestamp>`, so in consumer logs you can tell which topic each message came from. Default topics are `test-topic` and `test-topic-2`; override with `TOPICS="topic-a,topic-b"`.

## Viewing consumer message bodies (log sink)

If your consumer pipeline logs message payloads, you can filter for these bodies:

```bash
kubectl logs -n default -l app.kubernetes.io/name=cloud-consumer-pipeline-out -c numa --tail=5000 \
  | grep "Payload -" | grep -E "\[test-topic\]|\[test-topic-2\]" | sed -n 's/.*Payload - \(.*\) Keys -.*/\1/p'
```

Use a larger `--tail` if needed, and ensure the **out** pod is running and messages were produced after the consumer started.
