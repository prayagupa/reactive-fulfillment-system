"""
Wave generation algorithm.

A wave is a cluster of allocated orders assigned to a pick zone. The algorithm:
1. Pulls all ELIGIBLE orders for a given FC from PostgreSQL.
2. Scores each order by SLA urgency (nearest cutoff = highest score).
3. Clusters orders by warehouse zone (simple grouping by postal-code prefix here;
   real implementation uses bin-location zone mappings from the product catalogue).
4. Runs a nearest-neighbour TSP heuristic to sequence pick items within each zone.
5. Emits one WaveReleased + N PickList events per zone cluster.
"""

from __future__ import annotations

import time
import uuid
import math
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from app.db.postgres import get_session
from app.kafka.producer import publish_wave_released, publish_pick_list
from app.models import WaveOrder, Wave


@dataclass
class PickItem:
    item_seq: int
    sku: str
    quantity: int
    bin_location: str
    zone: str
    # Coordinates for TSP (row, col derived from bin_location e.g. "A-03-05")
    row: int = 0
    col: int = 0

    def __post_init__(self):
        parts = self.bin_location.split("-")
        if len(parts) >= 3:
            self.row = ord(parts[0][0]) - ord("A")
            try:
                self.col = int(parts[2])
            except ValueError:
                pass


def _nearest_neighbour_tsp(items: list[PickItem]) -> list[PickItem]:
    """
    Greedy nearest-neighbour TSP.
    Sequences pick items to minimise total travel distance within a zone.
    Time complexity O(n²) — acceptable for n < 500 items per zone.
    """
    if not items:
        return []

    unvisited = list(items)
    path = [unvisited.pop(0)]

    while unvisited:
        current = path[-1]
        nearest = min(
            unvisited,
            key=lambda x: math.sqrt((x.row - current.row) ** 2 + (x.col - current.col) ** 2),
        )
        path.append(nearest)
        unvisited.remove(nearest)

    return path


def _sla_score(order: WaveOrder) -> float:
    """Higher score = more urgent. Orders nearest SLA cutoff score highest."""
    now = datetime.now(tz=timezone.utc)
    if order.requested_delivery_date is None:
        return 0.5  # standard SLA
    delta_hours = (order.requested_delivery_date - now).total_seconds() / 3600
    # Invert: closer = higher score, clamp between 0 and 1
    return max(0.0, min(1.0, 1.0 - delta_hours / 48.0))


def generate_wave(fc_id: str) -> dict[str, Any]:
    """
    Generate a single wave for the given FC.
    Returns a summary dict with wave_id, order_count, zone_count.
    """
    with get_session() as session:
        eligible_orders: list[WaveOrder] = (
            session.query(WaveOrder)
            .filter(WaveOrder.fc_id == fc_id, WaveOrder.status == "ELIGIBLE")
            .limit(200)  # cap wave size
            .all()
        )

    if not eligible_orders:
        return {"wave_id": None, "order_count": 0, "zone_count": 0}

    # Sort by SLA score descending
    eligible_orders.sort(key=_sla_score, reverse=True)

    # Cluster by zone (stub: all orders go to zone "ZONE-A" until real bin-loc data is available)
    zone_map: dict[str, list[WaveOrder]] = {}
    for order in eligible_orders:
        zone = order.zone or "ZONE-A"
        zone_map.setdefault(zone, []).append(order)

    wave_id = str(uuid.uuid4())
    wave = Wave(wave_id=wave_id, fc_id=fc_id, status="RELEASED", order_count=len(eligible_orders))

    with get_session() as session:
        session.add(wave)
        for order in eligible_orders:
            order.status = "IN_WAVE"
            order.wave_id = wave_id
        session.commit()

    # Publish WaveReleased
    publish_wave_released(
        wave={"wave_id": wave_id, "priority": 1},
        order_ids=[o.order_id for o in eligible_orders],
        fc_id=fc_id,
    )

    # Publish individual PickList per order
    for order in eligible_orders:
        pick_items = _nearest_neighbour_tsp(order.pick_items or [])
        publish_pick_list({
            "wave_id": wave_id,
            "order_id": order.order_id,
            "fc_id": fc_id,
            "items": [
                {
                    "itemSeq": idx + 1,
                    "sku": item.sku,
                    "quantity": item.quantity,
                    "binLocation": item.bin_location,
                    "zone": item.zone,
                }
                for idx, item in enumerate(pick_items)
            ],
        })

    return {"wave_id": wave_id, "order_count": len(eligible_orders), "zone_count": len(zone_map)}
