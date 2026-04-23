# Benchmarking

## Automated CI Benchmarks

Every push to `main` and every pull request triggers the **Benchmark** workflow (`.github/workflows/benchmark.yml`). This runs an end-to-end performance test in a disposable Kubernetes cluster to measure consumer throughput and latency.

### What the workflow does

1. **Builds** the application image from the current commit using Jib
2. **Creates a k3d cluster** with Numaflow, JetStream ISB, and a standalone Pulsar instance
3. **Pre-fills** a Pulsar topic with ~4,000,000+ messages via a generator-based producer pipeline
4. **Deploys the consumer MonoVertex** and captures throughput, latency, and resource metrics over a 90-second measurement window
5. **Reports results** differently depending on the trigger:
    - **Push to `main`**: stores results on the [`gh-pages`](https://github.com/numaproj-contrib/apache-pulsar-java/tree/gh-pages) branch for historical tracking
    - **Pull request**: posts a sticky comment on the PR comparing the current metrics against the `main` baseline

### Manual trigger

The benchmark workflow supports `workflow_dispatch` with configurable parameters:

- **`measurement_duration`**: Duration of each measurement round in seconds (default: `90`)
- **`prefill_duration`**: How long to run the producer before starting the consumer in seconds (default: `500`)

To trigger manually, go to the [Actions tab](https://github.com/numaproj-contrib/apache-pulsar-java/actions/workflows/benchmark.yml) and click "Run workflow".

## Performance Charts on GitHub Pages

Benchmark results from every push to `main` are published to the `gh-pages` branch using [github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark). This produces an interactive chart that tracks performance over time.

**View the charts at: [https://numaproj-contrib.github.io/apache-pulsar-java/dev/bench/](https://numaproj-contrib.github.io/apache-pulsar-java/dev/bench/)**

The chart shows how key metrics change across commits, making it easy to spot regressions or improvements:

- **Throughput** (messages per second)
- **Latency** (end-to-end processing time)
- **Resource utilization** (CPU and memory)

An alert threshold of 200% is configured — if a commit causes metrics to degrade by more than 2x compared to the previous run, it will be flagged.

### PR Benchmark Comments

On pull requests, the workflow posts a sticky comment comparing the PR's benchmark results against the latest `main` baseline. This lets reviewers see the performance impact of a change before merging.

## Local Performance Testing

For more in-depth testing on your own cluster (e.g. testing different image builds, resource configurations, or Pulsar setups), see the [performance testing runbook](https://github.com/numaproj-contrib/apache-pulsar-java/tree/main/development/performance-testing).

The local runbook covers:

- Baseline parameters for fair comparison (replicas, batch size, CPU/memory, receiver queue)
- Setting up metrics with [numaflow-perfman](https://github.com/numaproj-labs/numaflow-perfman) (Prometheus + Grafana)
- Pre-filling topics and deploying the consumer MonoVertex
- Importing the Grafana dashboard for MonoVertex metrics
