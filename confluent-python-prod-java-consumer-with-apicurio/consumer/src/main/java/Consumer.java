/*
 * Copyright 2023 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.serde.Legacy4ByteIdHandler;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.types.ArtifactType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

/**
 * This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
 * scenario with JSON as the serialization type (and JSON Schema for validation).  Because JSON
 * Schema is only used for validation (not actual serialization), it can be enabled and disabled
 * without affecting the functionality of the serializers and deserializers.  However, if
 * validation is disabled, then incorrect data could be consumed incorrectly.
 *
 * The following aspects are demonstrated:
 *
 * <ol>
 *   <li>Register the JSON Schema in the registry</li>
 *   <li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
 *   <li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
 *   <li>Data sent as a MessageBean</li>
 * </ol>
 *
 * Pre-requisites:
 *
 * <ul>
 *   <li>Kafka must be running on localhost:9092</li>
 *   <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class Consumer {

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


    public static void main(String[] args) {
        System.out.println("Starting example " + Consumer.class.getSimpleName());
        String topicName = TOPIC_NAME;

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = TOPIC_NAME + "-value"; // use the topic name as the artifactId because we're going to map topic name to artifactId later on.
        RegistryClient client = RegistryClientFactory.create(REGISTRY_URL);
        var result = client.createArtifact("default", artifactId, ArtifactType.JSON, IfExists.RETURN_OR_UPDATE, new ByteArrayInputStream(SCHEMA.getBytes(StandardCharsets.UTF_8)));
        System.out.println("Registry said : " + result);

        // Create the consumer
        System.out.println("Creating the consumer.");

        try (KafkaConsumer<Long, MessageBean> consumer = createKafkaConsumer()) {
            System.out.println("Subscribing to topic " + topicName);
            // Subscribe to the topic
            consumer.subscribe(Collections.singletonList(topicName));

            // Consume the 5 messages.
            int messageCount = 0;
            System.out.println("Consuming (5) messages.");
            while (messageCount < 5) {
                final ConsumerRecords<Long, MessageBean> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else records.forEach(record -> {
                    Object msg = record.value();
                    System.out.println("Consumed a message: " + msg);
                });
            }
        }

        System.out.println("Done (success).");
    }

    /**
     * Creates the Kafka consumer.
     */
    private static KafkaConsumer<Long, MessageBean> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for JSON Schema
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonSchemaKafkaDeserializer.class.getName());

        if (Boolean.parseBoolean(System.getenv().getOrDefault("UseLegacy4ByteIdHandler", "false"))) {
            props.putIfAbsent(SerdeConfig.ID_HANDLER, Legacy4ByteIdHandler.class.getName());
        }

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // Enable validation in the deserializer to ensure that the data we receive is valid.
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);

        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload.  So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        // Create the Kafka Consumer
        KafkaConsumer<Long, MessageBean> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

}