# confluent-python-prod-java-consumer-with-apicurio

# scenerio

- Java Producer using Apicurio SerDes
- Confluent Python Consumer

TL;DR Apicurio SerDes identifies the schema by headers.  Confluent Python knows nothing about headers so it tries to process the payload bytes as if it were magic + int + payload.

## setup

### server side setup
```
docker run -p 8080:8080 apicurio/apicurio-registry:latest-snapshot
docker run -d -p 9092:9092 --name broker apache/kafka:latest
```


### python setup
```
virtualenv kafka-env
source kafka-env/bin/activate
pip install confluent-kafka six requests jsonschema
```

### register the schema

```
curl  -H 'Accept: application/vnd.schemaregistry.v1+json' 'http://localhost:8080/apis/ccompat/v7/subjects/example_serde_json-value/versions' -H 'Content-Type: application/vnd.schemaregistry.v1+json' -d@<(jq '. | {schema: tojson, schemaType: "JSON"}' < schema.json)
```

### running the producer

```
mvn -f producer/pom.xml compile exec:java
```

check the the producer is using headers

```
kafka-console-consumer --bootstrap-server localhost:9092  --topic example_serde_json --from-beginning --property print.headers=true
```

### running the consumer

```
consumer consumer/json_consumer.py -b localhost:9092 -s http://localhost:8080/apis/ccompat/v7
```

to get the Apicurio SerDer to produce records that are acceptable to the consumer, do this:

```
ENABLE_HEADERS=false ENABLE_CONFLUENT_ID_HANDLER=true mvn -f producer/pom.xml compile exec:java
```

# Error

This is the error the consumer will show as tries to interpret the first 4 bytes of the payload as a contentId.

```
Traceback (most recent call last):
  File "/Users/kwall/src/schema-hell/java-producer-with-apicurio-confluent-python-consumer/consumer/json_consumer.py", line 134, in <module>
    main(parser.parse_args())
  File "/Users/kwall/src/schema-hell/java-producer-with-apicurio-confluent-python-consumer/consumer/json_consumer.py", line 108, in main
    user = json_deserializer(msg.value(), SerializationContext(msg.topic(), MessageField.VALUE))
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/Users/kwall/src/schema-hell/confluent-python-prod-java-consumer-with-apicurio/kafka-env/lib/python3.12/site-packages/confluent_kafka/schema_registry/json_schema.py", line 366, in __call__

org.apache.kafka.common.errors.RecordDeserializationException: Error deserializing key/value for partition example_serde_json-0 at offset 0. If needed, please seek past the record to continue consumption.
    at org.apache.kafka.clients.consumer.internals.CompletedFetch.parseRecord (CompletedFetch.java:331)
    at org.apache.kafka.clients.consumer.internals.CompletedFetch.fetchRecords (CompletedFetch.java:283)
    at org.apache.kafka.clients.consumer.internals.FetchCollector.fetchRecords (FetchCollector.java:168)
    at org.apache.kafka.clients.consumer.internals.FetchCollector.collectFetch (FetchCollector.java:134)
    at org.apache.kafka.clients.consumer.internals.Fetcher.collectFetch (Fetcher.java:145)
    at org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer.pollForFetches (LegacyKafkaConsumer.java:693)
    at org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer.poll (LegacyKafkaConsumer.java:617)
    at org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer.poll (LegacyKafkaConsumer.java:590)
    at org.apache.kafka.clients.consumer.KafkaConsumer.poll (KafkaConsumer.java:874)
    at Consumer.main (Consumer.java:129)
    at org.codehaus.mojo.exec.ExecJavaMojo.doMain (ExecJavaMojo.java:358)
    at org.codehaus.mojo.exec.ExecJavaMojo.doExec (ExecJavaMojo.java:347)
    at org.codehaus.mojo.exec.ExecJavaMojo.lambda$execute$0 (ExecJavaMojo.java:269)
    at java.lang.Thread.run (Thread.java:1583)
Caused by: io.apicurio.registry.rest.client.exception.VersionNotFoundException: No version with global ID '10655788641' found.
    at io.apicurio.registry.rest.client.exception.ExceptionMapper.map (ExceptionMapper.java:45)
    at io.apicurio.registry.rest.client.impl.ErrorHandler.handleErrorResponse (ErrorHandler.java:64)
    at io.apicurio.rest.client.handler.BodyHandler.lambda$toSupplierOfType$1 (BodyHandler.java:55)
    at io.apicurio.rest.client.JdkHttpClient.sendRequest (JdkHttpClient.java:204)
```


