# JWT Token 
* The pipeline and config map in this folder gives an example of if you wanted to use a JWT token to authenticate 
* Note that the authParamMap CANNOT be used because the AuthenticationToken class does not have a constructor that takes in a Map or multiple parameters
* Therefore, the authParams for this class must be a String
* For more details see: https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/auth/AuthenticationToken.java
