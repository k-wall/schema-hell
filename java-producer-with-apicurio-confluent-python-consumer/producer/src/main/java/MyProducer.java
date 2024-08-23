

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;

public class MyProducer  {

    private static final String REGISTRY_URL = "http://localhost:8080/";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = "example_serde_json";
    public static final String SCHEMA = """
            {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "title": "User",
                "description": "A Confluent Kafka Python User",
                "type": "object",
                "properties": {
                  "name": {
                    "description": "User's name",
                    "type": "string"
                  },
                  "favorite_number": {
                    "description": "User's favorite number",
                    "type": "number",
                    "exclusiveMinimum": 0
                  },
                  "favorite_color": {
                    "description": "User's favorite color",
                    "type": "string"
                  }
                },
                "required": [
                  "name",
                  "favorite_number",
                  "favorite_color"
                ]
              }
            }
            """;


    public static final void main(String [] args) throws Exception {
        System.out.println("Starting example " + MyProducer.class.getSimpleName());
        String topicName = TOPIC_NAME;

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = TOPIC_NAME + "-value"; // use the topic name as the artifactId because we're going to map topic name to artifactId later on.
        RegistryClient client = RegistryClientFactory.create(REGISTRY_URL);
        client.createArtifact("default", artifactId, ArtifactType.JSON, IfExists.RETURN_OR_UPDATE, new ByteArrayInputStream(SCHEMA.getBytes(StandardCharsets.UTF_8)));

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 5 messages.

        try {
            System.out.println("Producing (5) messages.");
            Random random = new Random();
            for (int idx = 0; idx < 5; idx++) {
                // Create the message to send
                int favoriteNumber = Math.abs(random.nextInt());
                MessageBean message = new MessageBean(UUID.randomUUID().toString(), favoriteNumber + 1, random.nextDouble() < 0.5 ? "Blue" : "Red" );

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, UUID.randomUUID().toString(), message);
                producer.send(producedRecord);

                Thread.sleep(100);
            }
            System.out.println("Messages successfully produced.");
        } finally {
            System.out.println("Closing the producer.");
            producer.flush();
            producer.close();
        }

        System.out.println("Done (success).");
    }

    /**
     * Creates the Kafka producer.
     */
    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + TOPIC_NAME);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Use the Apicurio Registry provided Kafka Serializer for JSON Schema
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSchemaKafkaSerializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.FALSE);
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);

        var enableConfluentIdHandler = System.getenv().getOrDefault("ENABLE_CONFLUENT_ID_HANDLER", "false");
        var enableHeaders = System.getenv().getOrDefault("ENABLE_HEADERS", SerdeConfig.ENABLE_HEADERS_DEFAULT + "");
        System.out.println("Confluent id handler :" + enableConfluentIdHandler);
        System.out.println("Enable headers :" + enableHeaders);

        props.putIfAbsent(SerdeConfig.ENABLE_CONFLUENT_ID_HANDLER, enableConfluentIdHandler);
        props.putIfAbsent(SerdeConfig.ENABLE_HEADERS, enableHeaders);

        // Create the Kafka producer
        Producer<Object, Object> producer = new KafkaProducer<>(props);
        return producer;
    }

}
