package io.numaproj.pulsar.examples;

import io.numaproj.pulsar.model.numagen;
import io.numaproj.pulsar.model.DataRecord;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.concurrent.TimeUnit;

public class NumagenExample {

    public static void main(String[] args) {
        try {
            // Create Pulsar client
            PulsarClient client = PulsarClient.builder()
                    .serviceUrl("pulsar://localhost:6650")
                    .build();

            // Create producer
            Producer<numagen> producer = client.newProducer(Schema.AVRO(numagen.class))
                    .topic("persistent://public/default/numagen-topic")
                    .producerName("numagen-example-producer")
                    .sendTimeout(10, TimeUnit.SECONDS)
                    .create();

            // Method 1: Using the builder pattern (recommended)
            numagen message1 = numagen.newBuilder()
                    .setCreatedts(System.currentTimeMillis())
                    .setData(DataRecord.newBuilder()
                            .setValue(42L)
                            .setPadding("example")
                            .build())
                    .build();

            // Send message synchronously
            producer.send(message1);
            System.out.println("Message 1 sent successfully");

            // Method 2: Using constructor and setters
            numagen message2 = new numagen();
            message2.setCreatedts(System.currentTimeMillis());

            DataRecord data = new DataRecord();
            data.setValue(123L);
            data.setPadding("another example");
            message2.setData(data);

            // Send message asynchronously
            producer.sendAsync(message2)
                    .thenAccept(msgId -> {
                        System.out.println("Message 2 sent successfully, messageId: " + msgId);
                    })
                    .exceptionally(ex -> {
                        System.err.println("Failed to send message 2: " + ex);
                        return null;
                    });

            // Method 3: Using the all-args constructor
            DataRecord data3 = new DataRecord("third example", 789L);
            numagen message3 = new numagen(System.currentTimeMillis(), data3);

            // Send with key
            producer.newMessage()
                    .key("message-3")
                    .value(message3)
                    .send();
            System.out.println("Message 3 sent with key");

            // Data can be null (it's optional in the schema)
            numagen message4 = numagen.newBuilder()
                    .setCreatedts(System.currentTimeMillis())
                    .setData(null) // Data is optional
                    .build();

            // Send with properties
            producer.newMessage()
                    .property("messageType", "null-data")
                    .value(message4)
                    .sendAsync()
                    .thenAccept(msgId -> {
                        System.out.println("Message 4 sent with properties, messageId: " + msgId);
                    });

            // Wait for async operations to complete
            Thread.sleep(1000);

            // Clean up
            producer.close();
            client.close();

        } catch (PulsarClientException | InterruptedException e) {
            System.err.println("Error in Pulsar operations: " + e);
        }
    }
}