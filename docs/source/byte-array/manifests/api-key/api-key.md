# API Key 
The pipeline and config map in this folder demonstrate how to use a API key for authentication. Note that the authParamMap cannot be used, because the AuthenticationToken class does not have a constructor that accepts a Map or multiple parameters. Therefore, the authParams for this class must be provided as a single String. Although the admin and client configuration files reference different service URLs, they can still rely on the same key. 

For more details, see: https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/auth/AuthenticationToken.java
