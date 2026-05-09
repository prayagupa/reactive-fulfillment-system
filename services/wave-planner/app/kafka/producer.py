import os
import uuid
import time
from confluent_kafka.avro import AvroProducer

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
SCHEMA_REGISTRY = os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8081")

_producer: AvroProducer | None = None


def get_producer() -> AvroProducer:
    global _producer
    if _producer is None:
        _producer = AvroProducer(
            {
                "bootstrap.servers": KAFKA_BOOTSTRAP,
                "schema.registry.url": SCHEMA_REGISTRY,
            }
        )
    return _producer


def publish_wave_released(wave: dict, order_ids: list[str], fc_id: str, zone: str | None = None):
    producer = get_producer()
    event = {
        "waveId": wave["wave_id"],
        "fcId": fc_id,
        "orderIds": order_ids,
        "zone": zone,
        "priority": wave.get("priority", 1),
        "eventTime": int(time.time() * 1000),
    }
    producer.produce(topic="picklists", key=wave["wave_id"], value=event)
    producer.flush()


def publish_pick_list(pick_list: dict):
    producer = get_producer()
    event = {
        "pickListId": str(uuid.uuid4()),
        "waveId": pick_list["wave_id"],
        "orderId": pick_list["order_id"],
        "fcId": pick_list["fc_id"],
        "items": pick_list["items"],
        "eventTime": int(time.time() * 1000),
    }
    producer.produce(topic="picklists", key=pick_list["order_id"], value=event)
    producer.flush()
