# Contributing

## CI/CD Pipeline

This project uses an automated CI/CD pipeline with two GitHub Actions workflows.

### CI (`ci.yml`)

Runs on every push and pull request to `main`. The workflow has three jobs:

| Job | What it does | Runs on |
|---|---|---|
| **checkstyle** | `mvn checkstyle:check` — enforces code style rules defined in `checkstyle.xml` | Every push to main and PR |
| **build** | `mvn clean install` — compiles and runs all unit tests | Every push to main and PR |
| **coverage** | Runs tests with JaCoCo and posts a coverage report as a PR comment (min 50% overall, 50% for changed files) | PRs only |

All jobs must pass before a PR can be merged.

### Benchmark (`benchmark.yml`)

Runs on every push and pull request to `main` (also supports `workflow_dispatch` with configurable measurement and pre-fill durations). The workflow:

1. Builds the application image with Jib
2. Spins up a **k3d** cluster with Numaflow, JetStream ISB, and a standalone Pulsar instance
3. Pre-fills a Pulsar topic via a generator-based producer pipeline
4. Deploys the consumer MonoVertex and captures throughput, latency, and resource metrics
5. On pushes to `main`: stores results on the `gh-pages` branch for historical tracking
6. On PRs: posts a sticky comment comparing metrics against the `main` baseline

### Building a local Docker image

The [Jib](https://github.com/GoogleContainerTools/jib) Maven plugin is bound to the `package` phase, so a standard Maven build produces a Docker image automatically:

```bash
mvn clean package
```

By default this tags the image as `apache-pulsar-java:v${project.version}` (e.g. `apache-pulsar-java:v1.0.0`). To use a custom tag:

```bash
mvn clean package -Djib.to.image=apache-pulsar-java:<tag>
```

Use `-Djib.skip=true` to skip the image build if you only want to compile and run tests.

### Release (`release-please.yml`)

Releases are automated using [Release Please](https://github.com/googleapis/release-please). The workflow runs on every push to `main` and manages versioning, changelogs, and image publishing.

#### How it works

1. Push commits to `main` using [Conventional Commits](https://www.conventionalcommits.org/) format
2. Release Please automatically creates or updates a **release PR** with a version bump and changelog
3. When the release PR is merged, the workflow:
   - Creates a GitHub Release and git tag (e.g., `v0.4.0`)
   - Builds the Java application
   - Pushes the container image to Quay.io as `quay.io/numaio/numaflow-java/pulsar-java:<version>`

#### PR title format

This repo uses **squash merging**, so the PR title becomes the commit message on `main`. If you want your PR to trigger a version bump and release, the PR title must follow the previously mentioned conventional format:

| Prefix | Example | Version bump |
|---|---|---|
| `fix:` | `fix: handle null pointer in consumer` | Patch (0.4.0 → 0.4.1) |
| `feat:` | `feat: add batch message support` | Minor (0.4.0 → 0.5.0) |
| `feat!:` | `feat!: redesign config format` | Major (0.4.0 → 1.0.0) |

Other prefixes (`chore:`, `docs:`, `refactor:`, `test:`) won't trigger a version bump, won't appear in the changelog, and won't generate a release PR on their own.

#### Configuration files

| File | Purpose |
|---|---|
| `release-please-config.json` | Release Please settings (release type, snapshot config, title pattern) |
| `.release-please-manifest.json` | Tracks the current released version |