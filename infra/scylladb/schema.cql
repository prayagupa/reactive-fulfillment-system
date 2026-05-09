# ScyllaDB keyspace and table DDL for all domains.
# Applied via Liquibase Cassandra extension on service startup (or manual via cqlsh in dev).

# ── Keyspace ──────────────────────────────────────────────────────────────────
CREATE KEYSPACE IF NOT EXISTS shipping
  WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
  }
  AND durable_writes = true;

USE shipping;

# ── Order Ingestion ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
  order_id         UUID,
  idempotency_key  TEXT,
  customer_id      TEXT,
  status           TEXT,
  fc_id            TEXT,
  payload          TEXT,   -- JSON: items + shipping address
  created_at       TIMESTAMP,
  updated_at       TIMESTAMP,
  PRIMARY KEY (order_id)
);

CREATE INDEX IF NOT EXISTS ON orders (idempotency_key);

# ── Inventory ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS inventory_ledger (
  fc_id     TEXT,
  sku       TEXT,
  on_hand   INT,
  reserved  INT,
  allocated INT,
  PRIMARY KEY (fc_id, sku)
);

# ── Pick Engine ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pick_tasks (
  pick_list_id  TEXT,
  item_seq      INT,
  order_id      TEXT,
  sku           TEXT,
  bin_location  TEXT,
  quantity      INT,
  status        TEXT,    -- PENDING | ASSIGNED | PICKED | SHORT
  picked_by     TEXT,
  PRIMARY KEY (pick_list_id, item_seq)
) WITH CLUSTERING ORDER BY (item_seq ASC);

# ── Pack Service ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_dims (
  sku        TEXT PRIMARY KEY,
  weight_g   INT,
  length_mm  INT,
  width_mm   INT,
  height_mm  INT
);

CREATE TABLE IF NOT EXISTS shipments (
  shipment_id     UUID,
  order_id        TEXT,
  fc_id           TEXT,
  tracking_number TEXT,
  carrier         TEXT,
  service_level   TEXT,
  status          TEXT,
  label_url       TEXT,
  boxes           TEXT,    -- JSON
  created_at      TIMESTAMP,
  PRIMARY KEY (shipment_id)
);

CREATE INDEX IF NOT EXISTS ON shipments (order_id);

# ── Carrier Tracking ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tracking (
  tracking_number TEXT,
  event_time      TIMESTAMP,
  status          TEXT,
  location        TEXT,
  description     TEXT,
  PRIMARY KEY (tracking_number, event_time)
) WITH CLUSTERING ORDER BY (event_time DESC)
  AND default_time_to_live = 2592000;  -- 30-day TTL
