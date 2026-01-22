# Apache Pulsar Java 
Apache Pulsar user-defined source and sink implemented using Java. 

**Note for Production:** For StreamNative Cloud or secured clusters, see [Pulsar on StreamNative](docs/get-started/pulsar-on-streamnative.md)

## Getting Started: Local Development Setup

#### Run local Pulsar cluster and Pulsar Manager UI:
This starts a local Pulsar broker and the Pulsar Manager web interface for monitoring.

```bash
docker-compose up
```

#### Set Pulsar Manager account by running this in your terminal:
```
  CSRF_TOKEN=$(curl http://localhost:7750/pulsar-manager/csrf-token)
curl \
-H "X-XSRF-TOKEN: $CSRF_TOKEN" \
-H "Cookie: XSRF-TOKEN=$CSRF_TOKEN;" \
-H "Content-Type: application/json" \
-X PUT http://localhost:7750/pulsar-manager/users/superuser \
-d '{"name": "admin", "password": "apachepulsar", "description": "test", "email": "username@test.org"}'
```

#### Access Pulsar Manager UI:

Go to http://localhost:9527/#/login?redirect=%2F and login with the name and password fields specified in the command above.

In Pulsar Manager, once logged in, create an environment with the service URL http://pulsar:8080/ and Bookie url http://pulsar:8080/.<img width="809" alt="image" src="https://github.com/user-attachments/assets/293665d3-4528-4325-bbf7-0cbd7bc04928" />

#### Deploy to Numaflow:
Note: navigate to the ```docs``` folder, and go to either the sink/source subfolder (```docs/source/byte-array/``` or ```docs/sink/byte-array/```) for example producer and consumer pipeline and configmap configurations. For local Pulsar cluster, you can omit the ```authPluginClassName``` and ```authParams``` fields for the config map implementation.

```bash
# Build the project and create the Docker image
mvn clean install

# Deploy the pipeline and configuration to Kubernetes
kubectl apply -f <path-to-pipeline.yaml>
kubectl apply -f <path-to-config-map.yaml>

# Port-forward to access Numaflow UI
kubectl -n numaflow-system port-forward deployment/numaflow-server 8443:8443
```

Then access the Numaflow UI at https://localhost:8443/