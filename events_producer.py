#!/bin/env python3
import os
import yaml
import time
from random import randint
from datetime import datetime
from confluent_kafka import avro
from confluent_kafka.avro import AvroProducer, CachedSchemaRegistryClient


class EventProducer(object):
    """
    Produces bunch of events every interval
    """
    MSG_PER_BATCH = 3000
    BATCH_INTERVAL_MS = 30000
    CONFIG_FILE = 'config.yml'
    NO_OF_USERS = 2600
    EVENT_TYPES = ['pageview', 'click']

    def __init__(self):
        config = self.load_config(self.CONFIG_FILE)
        sc = CachedSchemaRegistryClient(url=config['kafkaSchemaRegistryUrl'])
        self.topic = config['kafkaTopics'][0]
        key_schema = sc.get_latest_schema(self.topic + "-key")[1]
        val_schema = sc.get_latest_schema(self.topic + "-value")[1]
        self.producer = AvroProducer({
            'bootstrap.servers': config['kafkaBootstrapServers'],
            'schema.registry.url': config['kafkaSchemaRegistryUrl']
        }, default_key_schema=key_schema, default_value_schema=val_schema)

    def load_config(self, config_file):
        if os.path.exists(config_file) and os.path.isfile(config_file):
            f = open(config_file, 'r')
            c = f.read()
            f.close()
            return yaml.load(c)
        else:
            raise ValueError("Config File not present")

    def produce(self):
        user_id = 'user' + str(randint(1, self.NO_OF_USERS))
        ts = int(datetime.now().timestamp() * 1000)
        event_type = self.EVENT_TYPES[randint(0, (len(self.EVENT_TYPES)-1))]
        key = user_id
        value = {'timestamp': ts,
                 'type': event_type,
                 'meta': {}}
        self.producer.produce(topic=self.topic,
                              value=value, key=key)


if __name__ == "__main__":
    print("Starting EventProducer...")
    ep = EventProducer()
    try:
        while True:
            for i in range(0, ep.MSG_PER_BATCH):
                ep.produce()
            wait_sec = ep.BATCH_INTERVAL_MS/1000
            print("Waiting for " + str(wait_sec) + " seconds before starting next batch")
            time.sleep(wait_sec)
    except KeyboardInterrupt:
        print('Stopping EventProducer...')
