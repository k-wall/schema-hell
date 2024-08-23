# confluent-python-prod-java-consumer-with-apicurio

# scenerio

- Confluent Python Producer
- Java Consumer using Apicurio SerDes


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
python producer/json_producer.py -b localhost:9092 -s http://localhost:8080/apis/ccompat/v7
```

check the the confluent producer is writing the magic + 4 bytes:

```
kafka-console-consumer --bootstrap-server localhost:9092  --topic example_serde_json --from-beginning | od -t x1

           vvvvvvvvvvvvvvvvvv
0000000    00  00  00  00  01  7b  22  6e  61  6d  65  22  3a  20  22  64
0000020    22  2c  20  22  66  61  76  6f  72  69  74  65  5f  6e  75  6d
```

### running the consumer

```
mvn -f consumer/pom.xml compile exec:java
```

To put the Apicurio SerDe into a mode where it can consume the 4 byte content id, do this:

```
ENABLE_CONFLUENT_ID_HANDLER=true mvn -f consumer/pom.xml compile exec:java
```

This tells the example to override the `IdHandler`.
https://www.apicur.io/registry/docs/apicurio-registry/2.6.x/getting-started/assembly-configuring-kafka-client-serdes.html#:~:text=headers.enabled.-,ID%20encoding,-You%20can%20customize


# Error

This is the error the consumer will show as it mistakenly tries to read magic + 8 bytes from the record value.


```
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


