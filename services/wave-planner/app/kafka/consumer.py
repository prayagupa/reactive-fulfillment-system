import asyncio
import os
from confluent_kafka.avro import AvroConsumer
from confluent_kafka import KafkaError

from app.db.postgres import get_session
from app.models import WaveOrder

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
SCHEMA_REGISTRY = os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8081")


async def start_consumer():
    """Launch Kafka consumer loop in a background thread (asyncio-friendly)."""
    loop = asyncio.get_event_loop()
    loop.run_in_executor(None, _consume_loop)


def _consume_loop():
    consumer = AvroConsumer({
        "bootstrap.servers": KAFKA_BOOTSTRAP,
        "group.id": "wave-planner",
        "auto.offset.reset": "earliest",
        "enable.auto.commit": False,
        "schema.registry.url": SCHEMA_REGISTRY,
    })
    consumer.subscribe(["inventory-events"])

    while True:
        msg = consumer.poll(timeout=1.0)
        if msg is None:
            continue
        if msg.error():
            if msg.error().code() == KafkaError._PARTITION_EOF:
                continue
            raise KafkaError(msg.error())

        event = msg.value()
        # Only react to InventoryReserved schema
        if event.get("schema", {}).get("name") == "InventoryReserved":
            _on_inventory_reserved(event)

        consumer.commit(message=msg)


def _on_inventory_reserved(event: dict):
    """Mark the order as wave-eligible in PostgreSQL."""
    order_id = event["orderId"]
    fc_id = event["fcId"]

    with get_session() as session:
        wo = WaveOrder(order_id=order_id, fc_id=fc_id, status="ELIGIBLE")
        session.merge(wo)
        session.commit()
