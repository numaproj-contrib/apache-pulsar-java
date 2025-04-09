
# API Key

This folder contains both a pipeline and a config map providing an example of using a API key for authentication. Note that the authParamMap cannot be used, because the AuthenticationToken class does not have a constructor that accepts a Map or multiple parameters. Therefore, the authParams for this class must be a single string. 

For more details, see: https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/auth/AuthenticationToken.java
