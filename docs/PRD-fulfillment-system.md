# Product Requirements Document: Warehouse Fulfillment System

**Version:** 1.0  
**Date:** May 9, 2026  
**Status:** Draft  

---

## 1. Executive Summary

This document defines the product requirements for a large-scale, reactive warehouse fulfillment system capable of handling millions of orders per day — from order ingestion through inventory replenishment. The system is designed to operate across multiple fulfillment centers (FCs), supporting end-to-end logistics with real-time visibility, high availability, and fault tolerance.

---

## 2. Goals & Objectives

| Goal | Description |
|------|-------------|
| **Scalability** | Handle up to 10M orders/day across 100+ fulfillment centers |
| **Latency** | Order-to-ship SLA of < 2 hours for same-day, < 24 hours for standard |
| **Accuracy** | > 99.95% pick/pack accuracy |
| **Availability** | 99.99% uptime for order processing pipeline |
| **Inventory Health** | Maintain in-stock rate > 98% for top-selling SKUs |

---

## 3. Stakeholders

- **Customers** — Expect accurate, on-time delivery
- **Warehouse Associates** — Pick, pack, and ship orders
- **Inventory Planners** — Manage stock levels and replenishment
- **Carriers / 3PLs** — Receive manifests and pickup instructions
- **Engineering & Operations** — Build, operate, and monitor the system
- **Finance** — Cost tracking per order, per FC

---

## 4. System Scope

### In Scope
- Order ingestion and validation
- Inventory reservation and allocation
- Wave planning and pick list generation
- Picking (single-unit, multi-unit, batch, zone)
- Packing and label generation
- Sortation and staging
- Carrier handoff and shipment tracking
- Returns processing
- Inventory replenishment (reactive and predictive)

### Out of Scope
- Last-mile delivery routing (carrier-owned)
- Vendor/supplier procurement systems
- Customer-facing order management UI

---

## 5. User Stories & Functional Requirements

### 5.1 Order Ingestion

**As a** system, **I need to** receive and validate orders from multiple channels (web, mobile, API, B2B EDI) so that only valid, fulfillable orders enter the pipeline.

| ID | Requirement |
|----|-------------|
| OR-01 | Accept orders via REST API, event stream (Kafka), and EDI |
| OR-02 | Validate order schema, payment status, and address |
| OR-03 | Deduplicate orders using idempotency keys |
| OR-04 | Route orders to the optimal FC based on inventory proximity, capacity, and carrier SLA |
| OR-05 | Emit `OrderReceived` event within 500ms of ingestion |
| OR-06 | Support order splitting across multiple FCs when no single FC can fulfill entirely |
| OR-07 | Expose order status API with < 100ms p99 latency |

---

### 5.2 Inventory Management

**As an** inventory planner, **I need** real-time visibility into stock levels and reservations so I can prevent overselling and stockouts.

| ID | Requirement |
|----|-------------|
| INV-01 | Maintain a real-time inventory ledger per SKU per FC |
| INV-02 | Reserve inventory atomically upon order receipt (soft hold) |
| INV-03 | Convert soft holds to hard allocations upon wave release |
| INV-04 | Release holds on order cancellation within 5 seconds |
| INV-05 | Support virtual bundles and kit BOM (bill of materials) explosion |
| INV-06 | Provide inventory APIs: available, reserved, on-hand, in-transit |
| INV-07 | Track inventory at lot, serial, and expiry levels |
| INV-08 | Support FIFO, FEFO, and LIFO picking strategies per category |

---

### 5.3 Wave Planning & Pick List Generation

**As a** warehouse operations manager, **I need** efficient wave plans so that associates can pick orders in batches to minimize travel time and maximize throughput.

| ID | Requirement |
|----|-------------|
| WP-01 | Generate pick waves every N minutes (configurable, default: 15 min) |
| WP-02 | Cluster orders by zone, carrier cutoff time, and priority |
| WP-03 | Support batch picking (1 associate picks for multiple orders) |
| WP-04 | Support zone picking with consolidation at pack station |
| WP-05 | Generate optimized pick paths using warehouse layout graph |
| WP-06 | Assign work to associates via handheld device / RF scanner |
| WP-07 | Dynamically re-route picks when inventory is found short |
| WP-08 | Prioritize SLA-at-risk orders within a wave |

---

### 5.4 Picking

**As a** warehouse associate, **I need** clear, step-by-step pick instructions so I can accurately pick items with minimal errors.

| ID | Requirement |
|----|-------------|
| PK-01 | Guide associate to bin location with aisle/bay/level/position |
| PK-02 | Confirm pick via barcode scan (item + quantity) |
| PK-03 | Support scan-to-verify (scan item, scan tote) |
| PK-04 | Detect and record pick shorts; trigger exception workflow |
| PK-05 | Support voice-directed picking as an alternative modality |
| PK-06 | Support robotic/AMR-assisted picking integration (API contract) |
| PK-07 | Real-time pick rate dashboard per associate, per zone |

---

### 5.5 Packing

**As a** pack station associate, **I need** the system to recommend the right box size and print the right label so I can pack orders quickly and correctly.

| ID | Requirement |
|----|-------------|
| PAK-01 | Recommend carton size using volumetric algorithm (items + dims) |
| PAK-02 | Print shipping label (carrier-compliant) on confirmation |
| PAK-03 | Print packing slip / gift message if applicable |
| PAK-04 | Capture packed weight; validate against estimated weight |
| PAK-05 | Flag weight discrepancies > 10% for review |
| PAK-06 | Support multi-box shipments for large orders |
| PAK-07 | Record packer ID, timestamp, and box barcode per shipment |
| PAK-08 | Emit `OrderPacked` event upon completion |

---

### 5.6 Sortation & Staging

**As a** dock manager, **I need** packed orders sorted to the correct carrier lane and staged before carrier pickup.

| ID | Requirement |
|----|-------------|
| SO-01 | Scan packed carton to assign to carrier sortation lane |
| SO-02 | Support conveyor-integrated auto-sortation via divert signals |
| SO-03 | Group cartons by carrier, service level, and route |
| SO-04 | Track staged carton count vs. carrier manifest |
| SO-05 | Alert when a carrier cutoff is approaching with pending volume |

---

### 5.7 Carrier Handoff & Shipment Tracking

**As a** customer, **I need** accurate tracking from the moment my order ships so I know when to expect delivery.

| ID | Requirement |
|----|-------------|
| SH-01 | Generate carrier manifest (EDI 856 / API) per pickup |
| SH-02 | Transmit tracking number to order management system within 60s of label creation |
| SH-03 | Ingest carrier scan events via webhook / polling |
| SH-04 | Detect and alert on shipment exceptions (delayed, lost, damaged) |
| SH-05 | Support multi-carrier: UPS, FedEx, USPS, regional carriers, own fleet |
| SH-06 | Emit `Shipped`, `OutForDelivery`, `Delivered` events to downstream systems |

---

### 5.8 Returns Processing

**As a** customer, **I need** to be able to return items and receive a refund or replacement quickly.

| ID | Requirement |
|----|-------------|
| RT-01 | Generate prepaid return label on request |
| RT-02 | Receive returned package; scan and capture condition |
| RT-03 | Route to restock, quarantine, or liquidation based on condition |
| RT-04 | Update inventory on-hand upon quality-passed restock |
| RT-05 | Trigger refund/replacement event to order management system |
| RT-06 | Report return reason codes for analytics |

---

### 5.9 Inventory Replenishment

**As an** inventory planner, **I need** the system to automatically trigger replenishment when stock falls below thresholds so we never go out of stock on high-velocity SKUs.

| ID | Requirement |
|----|-------------|
| RP-01 | Monitor real-time available inventory per SKU per FC |
| RP-02 | Trigger reactive replenishment when on-hand < reorder point |
| RP-03 | Calculate reorder point dynamically: `avg_daily_demand × lead_time + safety_stock` |
| RP-04 | Generate purchase order or transfer order to supplier/other FC |
| RP-05 | Support predictive replenishment using demand forecast (ML model integration) |
| RP-06 | Receive inbound POs: ASN ingestion, dock scheduling, putaway |
| RP-07 | Direct putaway to optimal bin location using slotting rules |
| RP-08 | Re-slot slow movers to reserve storage; promote fast movers to prime pick zones |
| RP-09 | Provide replenishment dashboards: pending POs, in-transit, expected arrival |

---

## 6. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Throughput** | Process 10,000 order events/sec at peak |
| **Latency** | Order routing decision < 500ms p99 |
| **Availability** | 99.99% uptime; no single point of failure |
| **Durability** | Zero order loss; event sourcing with at-least-once delivery |
| **Consistency** | Inventory reservation uses optimistic locking or saga pattern |
| **Observability** | Full distributed tracing, structured logging, real-time metrics |
| **Security** | Role-based access control (RBAC); all PII encrypted at rest and in transit |
| **Compliance** | GDPR, CCPA; audit trail for all inventory mutations |
| **Extensibility** | Plugin architecture for new carrier integrations |

---

## 7. System Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Order Channels                                   │
│         (Web / Mobile / B2B API / EDI / Marketplace)                    │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │  Order      │
                    │  Ingestion  │  ← Validate, Deduplicate, Route
                    └──────┬──────┘
                           │ OrderReceived event
          ┌────────────────┼────────────────┐
          │                │                │
   ┌──────▼──────┐  ┌──────▼──────┐  ┌─────▼──────┐
   │  Inventory  │  │    Wave     │  │  Carrier   │
   │  Service    │  │  Planner    │  │  Routing   │
   └──────┬──────┘  └──────┬──────┘  └─────┬──────┘
          │                │               │
   ┌──────▼──────┐  ┌──────▼──────┐        │
   │  Pick List  │  │   Picking   │        │
   │  Generator  │  │   Engine    │        │
   └─────────────┘  └──────┬──────┘        │
                           │               │
                    ┌──────▼──────┐        │
                    │   Pack &    │        │
                    │   Label     │        │
                    └──────┬──────┘        │
                           │               │
                    ┌──────▼──────┐        │
                    │  Sortation  │        │
                    │  & Staging  │        │
                    └──────┬──────┘        │
                           └───────────────┘
                                   │
                           ┌───────▼───────┐
                           │  Carrier      │
                           │  Handoff      │
                           └───────┬───────┘
                                   │
                           ┌───────▼───────┐
                           │  Tracking &   │
                           │  Delivery     │
                           └───────┬───────┘
                                   │
                           ┌───────▼───────┐
                           │  Returns &    │
                           │  Replenish-   │
                           │  ment         │
                           └───────────────┘
```

**Event backbone:** Kafka (or equivalent) for all domain events  
**State stores:** Distributed databases with strong consistency for inventory ledger  
**APIs:** REST + gRPC for internal services; REST for external integrations  

---

## 8. Key Domain Events

| Event | Producer | Consumers |
|-------|----------|-----------|
| `OrderReceived` | Ingestion Service | Inventory, Wave Planner, Carrier Router |
| `InventoryReserved` | Inventory Service | Order Service, Wave Planner |
| `WaveReleased` | Wave Planner | Pick Engine, Associate Devices |
| `PickCompleted` | Pick Engine | Pack Station, Inventory |
| `OrderPacked` | Pack Service | Sortation, Tracking |
| `OrderShipped` | Carrier Service | Order Mgmt, Customer Notifications |
| `OrderDelivered` | Carrier Service | Order Mgmt, Analytics, Finance |
| `ReturnReceived` | Returns Service | Inventory, Order Mgmt, Finance |
| `ReplenishmentTriggered` | Inventory Service | Procurement / Transfer Service |
| `PutawayCompleted` | Inbound Service | Inventory Service |

---

## 9. Data Model (Conceptual)

### Order
```
Order {
  order_id, channel, customer_id, placed_at,
  items: [{ sku, quantity, unit_price }],
  shipping_address, service_level, carrier,
  fc_id, status, split_shipments: []
}
```

### Inventory Ledger
```
InventoryLedger {
  fc_id, sku, on_hand, reserved, available,
  in_transit, reorder_point, safety_stock,
  last_updated
}
```

### Shipment
```
Shipment {
  shipment_id, order_id, fc_id, carrier, tracking_number,
  label_url, packed_at, shipped_at, delivered_at,
  status, boxes: [{ box_id, weight, dims, items: [] }]
}
```

---

## 10. Metrics & KPIs

| Metric | Target |
|--------|--------|
| Order-to-Ship Time (same-day) | < 2 hours |
| Order-to-Ship Time (standard) | < 24 hours |
| Pick Accuracy Rate | > 99.95% |
| Inventory Accuracy | > 99.9% |
| In-Stock Rate (top SKUs) | > 98% |
| Carrier On-Time Rate | > 97% |
| Return Processing Time | < 48 hours to restock |
| System Uptime | 99.99% |
| Order Defect Rate | < 0.1% |

---

## 11. Phased Rollout

### Phase 1 — Core Fulfillment (Q3 2026)
- Order ingestion, inventory reservation, pick/pack, label generation, carrier handoff

### Phase 2 — Operational Intelligence (Q4 2026)
- Wave optimization, batch/zone picking, real-time associate dashboards, weight validation

### Phase 3 — Replenishment & Returns (Q1 2027)
- Reactive replenishment, inbound PO management, putaway, returns processing

### Phase 4 — Predictive & Autonomous (Q2 2027)
- ML-based demand forecasting, predictive replenishment, AMR/robotics integration, auto-slotting

---

## 12. Open Questions

- [ ] What is the target number of FCs at launch vs. long-term?
- [ ] Will robotics (AMR/AS-RS) be in scope for Phase 1?
- [ ] Which WMS (Warehouse Management System) will this integrate with or replace?
- [ ] What are the carrier SLA commitments and cutoff times per region?
- [ ] How will inventory be reconciled between physical counts and the ledger?
- [ ] What is the expected peak order volume (Prime Day / holiday)?
- [ ] Will the system support drop-shipping from vendor warehouses?

---

*This document is a living artifact and will be updated as requirements are refined through discovery sessions with engineering, operations, and business stakeholders.*
