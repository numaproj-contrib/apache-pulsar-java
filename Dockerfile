# Use the official Amazon Corretto JDK 11 image as the base image
FROM amazoncorretto:11

# Establish the working directory inside the Docker container
WORKDIR /usr/src/app

# Copy the Maven configuration file
COPY pom.xml ./

# Optimize the build cache by downloading dependencies first; this layer gets rebuilt only if pom.xml changes
RUN yum install -y maven && \
    mvn dependency:resolve

# Copy the whole project. This assumes your whole project directory is structured for Maven.
COPY . .

# Build the project
RUN mvn clean package

# We assume your artifact is built to the target/ directory and is a fat JAR, i.e., a JAR with dependencies included
# Adjust the jar file name as needed
CMD ["java", "-jar", "target/apache-pulsar-java-1.0-SNAPSHOT-jar-with-dependencies.jar"]
