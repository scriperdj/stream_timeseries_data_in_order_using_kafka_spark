package me.sathish.example.spark

import org.apache.spark.{SparkConf, Partitioner}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Seconds, StreamingContext}

import org.apache.spark.streaming.kafka010.ConsumerStrategies._
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.HasOffsetRanges
import org.apache.kafka.common.TopicPartition
import org.apache.spark.TaskContext
import org.apache.avro.generic.{GenericData}
import org.apache.avro.util.Utf8
import io.confluent.kafka.serializers.{KafkaAvroDeserializer}


import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor

import java.io.{File, FileInputStream}
import java.util.HashMap
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.beans.BeanProperty

object SortedStreamProcessor {

  def main(args: Array[String]) = {
    val input = new FileInputStream(new File(args(0)))
    val yaml = new Yaml(new Constructor(classOf[Config]))
    val config = yaml.load(input).asInstanceOf[Config]
    var appName = this.getClass.getSimpleName

    val sparkConf = new SparkConf().setAppName(appName)
    .set("spark.streaming.stopGracefullyOnShutdown", "true")

    val sparkSession: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = sparkSession.sparkContext

    import sparkSession.implicits._

    val streamingContext = new StreamingContext(sc,Seconds(config.sparkBatchInterval))

    val kafkaParams = scala.collection.immutable.Map[String, Object](
      "bootstrap.servers" -> config.kafkaBootstrapServers,
      "key.deserializer" -> classOf[KafkaAvroDeserializer],
      "value.deserializer" -> classOf[KafkaAvroDeserializer],
      "schema.registry.url" -> config.kafkaSchemaRegistryUrl,
      "group.id" -> config.kafkaConsumerGroupID,
      "auto.offset.reset" -> config.kafkaOffset,
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val stream = KafkaUtils.createDirectStream[String, GenericData.Record](
      streamingContext,
      PreferConsistent,
      Subscribe[String, GenericData.Record](config.kafkaTopics, kafkaParams)
    )

    stream.foreachRDD((rdd,time) => {
      val eventsRDD = rdd.map(rec => {
        val event = rec.value()
        val userId = rec.key()
        val meta = event.get("meta").asInstanceOf[HashMap[Utf8, Utf8]]
          .asScala.map(x => (new String(x._1.getBytes, "UTF-8"), new String(x._2.getBytes, "UTF-8"))).toMap
        Event(time.toString, rec.partition,event.get("timestamp").asInstanceOf[Long],
              userId,
              new String(event.get("type").asInstanceOf[Utf8].getBytes, "UTF-8"),
              meta)
      })

      eventsRDD.foreachPartition(data => {
        var partition: Int = TaskContext.getPartitionId
        // Processing logic
        for ((event, i) <- data.zipWithIndex) {
          println(s"$partition - $i -> " + event.asInstanceOf[Event])
        }
      })
    })

    streamingContext.start()
    streamingContext.awaitTermination()

  }

  case class Event(batch: String, partition: Int, timestamp: Long, userId: String, eventType: String, meta: Map[String, String])

  class Config {
    @BeanProperty var kafkaBootstrapServers: String = _
    @BeanProperty var kafkaSchemaRegistryUrl: String = _
    @BeanProperty var kafkaConsumerGroupID: String = _
    @BeanProperty var kafkaOffset: String = _
    @BeanProperty var kafkaTopics: Array[String] = _
    @BeanProperty var sparkBatchInterval: Int = _
  }

}
