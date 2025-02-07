# Apache Pulsar Java 
Apache Pulsar user-defined source and sink implemented using Java. 

## Getting Started
#### Run Pulsar and Pulsar Manager UI:
``` docker-compose up```

- locally, go to http://localhost:9527/#/login?redirect=%2F

#### Numaflow:
*     mvn clean install
*     kubectl apply -f pipeline.yaml
*     kubectl -n numaflow-system port-forward deployment/numaflow-server 8443:8443

This builds the UDsink image, and the pipeline file uses it. Go to https://localhost:8443/ to use Numaflow UI

#### Pulsar Manager set up account:
````
  CSRF_TOKEN=$(curl http://localhost:7750/pulsar-manager/csrf-token)
curl \
-H "X-XSRF-TOKEN: $CSRF_TOKEN" \
-H "Cookie: XSRF-TOKEN=$CSRF_TOKEN;" \
-H "Content-Type: application/json" \
-X PUT http://localhost:7750/pulsar-manager/users/superuser \
-d '{"name": "admin", "password": "apachepulsar", "description": "test", "email": "username@test.org"}'
````


