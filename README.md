# Example Spark Application for processing streaming timeseries data from Kafka in order

This repo has a .py script that produces sample events events and a Spark application that consumes the messages in same order. Its used for explaining the concepts in the the [blog post I had written](http://sathish.me/2019/01/26/tips-for-processing-streaming-timeseries-data-from-kafka-in-order-using-spark.html).

## Dependency

* Kafka cluster with 3 brokers & a schema-registry within Docker VM for this application. The docker-compose file used for the setup is included in the git repo.

* Spark version 2.3 single node psudo cluster

## Build

```bash
> sbt package
```

## Execution

* Create Kafka Topic with 8 partitions for highlighting data distribution & parallelism.

```bash
> kafka-topics --create --topic events --partitions 8 --replication-factor 1 --if-not-exists --zookeeper localhost:42181
```

* Create schemas for key & value of the topic in schema-registry

```bash
> curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" --data '{"schema": "{\"type\":\"string\",\"name\":\"key\",\"doc\":\"User id of the event\"}"}' http://localhost:18081/subjects/events-key/versions

> curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" --data '{"schema": "{\"type\":\"record\",\"name\":\"events\",\"doc\":\"Stream of web events\",\"fields\":[{\"type\":\"long\",\"name\":\"timestamp\",\"doc\":\"Timestamp in epoch format\"},{\"type\":\"string\",\"name\":\"type\",\"doc\":\"Type of event\"},{\"type\":{\"type\":\"map\",\"values\":\"string\"},\"name\":\"meta\",\"doc\":\"Meta data about the event\"}]}"}' http://localhost:18081/subjects/events-value/versions
```

* Install packages from requirements.txt, change the configs in config.yml if required & start the event producer

```bash
> python events_producer.py
```

* Submit Spark job

```bash
> spark-submit --executor-cores 4 --num-executors 4 --class me.sathish.example.spark.SortedStreamProcessor --packages org.apache.spark:spark-streaming_2.11:2.3.0,org.apache.spark:spark-streaming-kafka-0-10_2.11:2.3.0,org.yaml:snakeyaml:1.18,io.confluent:kafka-avro-serializer:3.3.0,org.apache.kafka:kafka_2.11:0.11.0.0-cp1 --repositories http://packages.confluent.io/maven/ --master yarn target/scala-2.11/kafka_stream_processing_sorted_order_2.11-1.0.jar config.yml
```
