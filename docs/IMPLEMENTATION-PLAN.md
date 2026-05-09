# Implementation Plan: Warehouse Fulfillment System

**Version:** 1.0  
**Date:** May 9, 2026  
**Reference SDS:** [SDS-fulfillment-system.md](./SDS-fulfillment-system.md)  
**Reference PRD:** [PRD-fulfillment-system.md](./PRD-fulfillment-system.md)

---

## 1. Guiding Principles

- **Vertical slices over horizontal layers** — Each squad owns a domain end-to-end (API → DB → Kafka → tests); no shared infra team gate
- **Walking skeleton first** — Get a single order flowing through all domains (even with stubs) before optimizing any single domain
- **Infrastructure as Code from Day 1** — No manual cluster setup; every component provisioned via Helm + ArgoCD
- **Test in production shape** — Testcontainers for unit/integration; full Kafka + ScyllaDB + Valkey stack in CI
- **Feature flags everywhere** — New domain features hidden behind Unleash flags; dark-launch before cutover

---

## 2. Team Structure

| Squad | Domain Ownership | Size |
|-------|-----------------|------|
| **Platform** | Kubernetes, Kafka, ScyllaDB, Valkey, Istio, Harbor, ArgoCD, Observability | 4 engineers + 1 SRE |
| **Order** | Order Ingestion Service, FC Router, Kong Gateway | 3 engineers |
| **Inventory** | Inventory Service, OpenSearch read model | 3 engineers |
| **Fulfillment Ops** | Wave Planner, Pick Engine | 3 engineers |
| **Logistics** | Pack Service, Sortation & Staging | 3 engineers |
| **Carrier** | Carrier & Tracking Service, EDI pipeline (Apache Camel) | 3 engineers |
| **Post-Ship** | Returns Service, analytics (Trino + MinIO) | 2 engineers |
| **Replenishment** | Replenishment Service, Flink jobs, MLflow, Apache Airflow | 3 engineers |
| **Frontend** | Pack Station UI, Dock Management UI, Condition Capture UI | 2 engineers |
| **QA / Performance** | Contract tests (Pact), load tests (k6), chaos (Chaos Mesh) | 2 engineers |

**Total:** ~28 engineers across 10 squads  
**Ceremonies:** 2-week sprints; cross-squad sync weekly; architecture review monthly

---

## 3. Phases Overview

| Phase | Name | Duration | Goal |
|-------|------|----------|------|
| **0** | Foundation | Weeks 1–4 | Platform infra, CI/CD, shared libraries, skeleton services |
| **1** | Core Fulfillment | Weeks 5–16 | Order → Inventory → Pick → Pack → Ship (MVP flow) |
| **2** | Operational Intelligence | Weeks 17–24 | Wave optimization, sortation, real-time dashboards, alerting |
| **3** | Post-Ship & Returns | Weeks 25–32 | Returns processing, carrier exception handling, analytics |
| **4** | Replenishment | Weeks 33–40 | Reactive + predictive replenishment, Flink, ML forecasting |
| **5** | Scale & Hardening | Weeks 41–48 | Chaos testing, performance tuning, multi-FC cell rollout |

---

## 4. Phase 0 — Foundation (Weeks 1–4)

### 4.1 Objectives
Bootstrap infrastructure and shared development tooling so every squad can start in parallel from Week 5.

### 4.2 Platform Squad Tasks

#### Week 1–2: Core Infrastructure
- [ ] Provision Kubernetes clusters (3 environments: `dev`, `staging`, `prod`)
- [ ] Deploy **Istio 1.23** service mesh with mTLS enabled cluster-wide
- [ ] Deploy **Harbor** registry; configure Trivy scanning on push
- [ ] Bootstrap **ArgoCD**; create App-of-Apps structure (one ArgoCD App per domain)
- [ ] Configure **GitHub Actions** pipelines: lint → test → build → push → ArgoCD sync
- [ ] Create mono-repo structure:
  ```
  /services/
    order-ingestion/
    inventory/
    wave-planner/
    pick-engine/
    pack-service/
    sortation/
    carrier-tracking/
    returns/
    replenishment/
  /infra/
    kafka/
    scylladb/
    valkey/
    opensearch/
    postgres/
    observability/
  /libs/
    common-events/       ← Avro schemas + generated Java/Python classes
    common-security/     ← JWT validation, OPA policy helpers
    common-kafka/        ← Spring Boot 4 Kafka producer/consumer config
  ```

#### Week 2–3: Data Layer
- [ ] Deploy **Apache Kafka 3.8** (KRaft, 9-broker cluster) via Strimzi Operator
- [ ] Deploy **Confluent Schema Registry OSS**; publish initial Avro schemas for all domain events
- [ ] Deploy **ScyllaDB 6** cluster (3 nodes minimum; rack-aware replication factor 3)
- [ ] Deploy **PostgreSQL 17** via CloudNativePG operator (Wave Planner + Replenishment)
- [ ] Deploy **Valkey 8** cluster (3-node Redis-compatible)
- [ ] Deploy **OpenSearch 2.x** via OpenSearch Operator (Inventory read model)
- [ ] Deploy **MinIO** (multi-tenant, 3-node; analytics + audit log store)

#### Week 3–4: Observability & Security
- [ ] Deploy **Prometheus 3 + Grafana 11** stack (kube-prometheus-stack Helm chart)
- [ ] Deploy **Jaeger 2** + **OpenTelemetry Collector** (OTLP gateway)
- [ ] Deploy **Fluent Bit** DaemonSet → OpenSearch log pipeline
- [ ] Deploy **Alertmanager** + PagerDuty + Slack webhooks
- [ ] Deploy **HashiCorp Vault** (HA mode, 3 replicas); configure Vault Agent Injector
- [ ] Deploy **Keycloak 25** (external auth); configure OIDC for service UIs
- [ ] Deploy **SPIFFE/SPIRE** for workload identity (SVID per pod)
- [ ] Deploy **Unleash** (self-hosted feature flags)
- [ ] Deploy **Kong Gateway OSS** as edge API gateway

### 4.3 All Squads: Shared Library Bootstrapping

- [ ] Publish `common-events` library: generate Java + Python Avro stubs from all event schemas
- [ ] Publish `common-kafka` library: Spring Boot 4 auto-configuration for producers/consumers with OTel tracing
- [ ] Publish `common-security` library: JWT validation filter + OPA policy bundle loader
- [ ] Establish Testcontainers base image with Kafka + ScyllaDB + Valkey for integration tests
- [ ] Write ADR-001: Database selection (ScyllaDB vs. Cassandra vs. MongoDB)
- [ ] Write ADR-002: Messaging (Kafka vs. NATS vs. Pulsar)
- [ ] Write ADR-003: Service mesh (Istio vs. Linkerd)

### 4.4 Phase 0 Exit Criteria
- [ ] `Hello World` Spring Boot 4 service deploys to `staging` via ArgoCD in < 5 min
- [ ] Kafka topic created; producer sends; consumer reads with Schema Registry validation
- [ ] ScyllaDB: read/write latency < 1ms p99 in cluster
- [ ] All secrets pulled from Vault; no hardcoded credentials in any repo
- [ ] Prometheus scraping all infra components; Grafana dashboard showing cluster health

---

## 5. Phase 1 — Core Fulfillment (Weeks 5–16)

> **Goal:** A real order enters the system, inventory is reserved, items are picked, packed, labelled, and a shipment is created.

### 5.1 Sprint 1 (Weeks 5–6): Order Ingestion + Inventory Skeleton

**Order Squad**
- [ ] Scaffold `order-ingestion` service: Spring Boot 4 + WebFlux
- [ ] `POST /orders` endpoint: schema validation, idempotency key check (Valkey)
- [ ] Persist order to ScyllaDB (`orders` table; single-table design)
- [ ] Publish `OrderReceived` Avro event to Kafka `orders` topic
- [ ] Unit tests (JUnit 5 + Testcontainers); contract tests (Pact provider)
- [ ] Kong Gateway route: `/api/v1/orders` → order-ingestion service

**Inventory Squad**
- [ ] Scaffold `inventory` service: Spring Boot 4
- [ ] ScyllaDB schema: `inventory_ledger (fc_id, sku, on_hand, reserved, allocated)`
- [ ] Consumer: `OrderReceived` → soft-reserve inventory (Valkey Lua atomic script)
- [ ] Publish `InventoryReserved` or `InventoryInsufficient` event
- [ ] `GET /inventory/{fc_id}/{sku}` API

**Platform**
- [ ] Create Kafka topics: `orders`, `inventory-events`, `picklists`, `shipments`, `tracking-events`, `returns`, `replenishment`
- [ ] ScyllaDB keyspace + table DDL automation (Liquibase Cassandra extension or manual migration scripts in Git)

---

### 5.2 Sprint 2 (Weeks 7–8): FC Router + Order Splitting

**Order Squad**
- [ ] `fc-router` module (embedded in order-ingestion or separate service)
- [ ] Routing algorithm: score FCs by (distance rank × available inventory × carrier SLA window)
- [ ] Order splitting logic: if no single FC can fulfill → generate child orders in ScyllaDB
- [ ] Publish `OrderRouted` event; update order status
- [ ] Integration test: 3-FC routing decision in < 200ms p99

**Inventory Squad**
- [ ] Hard-allocation flow: consume `WaveReleased` (stub) → convert soft-hold → allocated
- [ ] Saga compensation: consume `OrderCancelled` → release reservation within 5s
- [ ] OpenSearch indexer: consume `inventory-events` → update OpenSearch read model
- [ ] `GET /inventory/search?fc=X&category=Y` via OpenSearch

---

### 5.3 Sprint 3 (Weeks 9–10): Wave Planner

**Fulfillment Ops Squad**
- [ ] Scaffold `wave-planner` service: Python 3.12 + FastAPI
- [ ] PostgreSQL schema: `waves`, `wave_orders`, `pick_lists`, `pick_items`
- [ ] Quartz-equivalent (APScheduler): trigger wave generation every 15 min
- [ ] Wave algorithm: query allocated orders from ScyllaDB → score → cluster by zone → TSP path
- [ ] Publish `WaveReleased` event + individual `PickList` events to Kafka
- [ ] REST API: `GET /waves/{fc_id}` , `GET /picklists/{wave_id}`
- [ ] Consume `InventoryReserved` to mark orders eligible for wave inclusion
- [ ] Unit tests: TSP path generation correctness; wave priority ordering

---

### 5.4 Sprint 4 (Weeks 11–12): Pick Engine

**Fulfillment Ops Squad**
- [ ] Scaffold `pick-engine` service: Spring Boot 4 + Netty WebSocket
- [ ] ScyllaDB schema: `pick_tasks (picklist_id, item_seq, sku, bin_location, status, picked_by)`
- [ ] Consume `PickList` event → create pick tasks in ScyllaDB
- [ ] WebSocket endpoint: associate device connects, receives pick instructions
- [ ] Scan confirmation: validate barcode → update task → publish `PickItemConfirmed`
- [ ] Pick short flow: publish `PickShort` → Kafka exception topic → Wave Planner re-route stub
- [ ] All pick tasks done → publish `PickCompleted` event
- [ ] Prometheus metrics: `pick_rate_per_associate`, `pick_error_rate`

---

### 5.5 Sprint 5 (Weeks 13–14): Pack Service + Label Generation

**Logistics Squad**
- [ ] Scaffold `pack-service`: Spring Boot 4
- [ ] Consume `PickCompleted` → present order at pack station
- [ ] 3D bin-packing Python microservice: `POST /carton-recommend` (gRPC)
- [ ] ScyllaDB table: `product_dims (sku, weight_g, length_mm, width_mm, height_mm)`
- [ ] ZPL label generation: template engine → raw TCP to Zebra printer
- [ ] EasyPost SDK integration: rate shop → purchase label → capture tracking number
- [ ] Capture scale weight via Go agent (serial/USB); weight validation (flag if > 10% delta)
- [ ] ScyllaDB table: `shipments (shipment_id, order_id, fc_id, tracking_number, status, boxes)`
- [ ] Publish `OrderPacked` event to `shipments` topic
- [ ] Pack Station UI (React 18): consume WebSocket; display items; confirm pack; trigger label print

---

### 5.6 Sprint 6 (Weeks 15–16): Carrier Handoff + End-to-End Walking Skeleton

**Carrier Squad**
- [ ] Scaffold `carrier-tracking` service: Spring Boot 4
- [ ] Consume `OrderPacked` → generate carrier manifest (Apache Camel + X12 856 EDI)
- [ ] Transmit tracking number back to order service within 60s of label creation
- [ ] Webhook receiver endpoint: `POST /webhooks/carrier/{carrier_id}`
- [ ] Normalize carrier event → `TrackingEvent` Avro → Kafka `tracking-events` topic
- [ ] ScyllaDB wide-row table: `tracking (tracking_number, event_time, status, location)`
- [ ] Publish `OrderShipped` event

**All Squads — Integration Week**
- [ ] Wire full flow: `POST /orders` → routed → reserved → wave → picked → packed → shipped
- [ ] Run k6 load test: 100 RPS sustained for 30 min; all services stable
- [ ] Fix integration issues; update Pact contracts
- [ ] Demo to stakeholders: live order through full pipeline

### 5.7 Phase 1 Exit Criteria
- [ ] Full order lifecycle completes end-to-end in `staging`
- [ ] p99 order routing < 200ms; p99 inventory reservation < 50ms
- [ ] Zero order loss under 100 RPS sustained load (30 min)
- [ ] All Pact contracts passing in CI
- [ ] Grafana dashboard showing order pipeline metrics

---

## 6. Phase 2 — Operational Intelligence (Weeks 17–24)

> **Goal:** Production-grade wave optimization, sortation, real-time ops dashboards, weight validation, and full alerting.

### 6.1 Sprint 7 (Weeks 17–18): Sortation & Staging

**Logistics Squad**
- [ ] Scaffold `sortation` service: Spring Boot 4
- [ ] Consume `OrderPacked` → assign carton to carrier lane (ScyllaDB `carton_lane_assignments`)
- [ ] OPC-UA client (Eclipse Milo): send divert signal to conveyor PLC (mock PLC in dev)
- [ ] PostgreSQL: `lane_manifests (lane_id, carrier, route, carton_count, cutoff_time)`
- [ ] Dock Management UI: React 18 + WebSocket; real-time carton count per lane
- [ ] Alertmanager rule: fire alert if lane carton count < expected 30 min before carrier cutoff
- [ ] NATS JetStream: cutoff alert → ops Slack channel

---

### 6.2 Sprint 8 (Weeks 19–20): Wave Optimization + Batch Picking

**Fulfillment Ops Squad**
- [ ] Zone picking: cluster pick tasks by warehouse zone; assign zone to associate
- [ ] Batch picking: single associate carries tote for N orders; consolidate at pack station
- [ ] Carrier cutoff-aware wave scoring: orders nearest cutoff → highest wave priority
- [ ] SLA-at-risk re-queue: detect orders > 80% of SLA time remaining → bump priority
- [ ] Pick Engine: exception workflow — pick short → wave planner re-slots from alternate bin
- [ ] Grafana dashboard: wave efficiency (orders/wave, pick rate, zone utilization)

---

### 6.3 Sprint 9 (Weeks 21–22): Carrier Exception Detection + Tracking

**Carrier Squad**
- [ ] Temporal workflow: `ShipmentMonitorWorkflow` — poll tracking events; detect stuck shipments (no scan for > 24h)
- [ ] Escalation: `ShipmentDelayed` event → NATS → Notification Service → customer OMS
- [ ] Multi-carrier normalization: UPS, FedEx, USPS, regional carrier webhook mappers
- [ ] `GET /tracking/{tracking_number}` API (reads ScyllaDB timeline)
- [ ] Carrier cutoff sync: Quartz job → poll carrier APIs → update Valkey cutoff store

---

### 6.4 Sprint 10 (Weeks 23–24): Observability Hardening + Load Testing

**Platform + QA Squad**
- [ ] Grafana dashboards: per-domain SLI/SLO panels (error rate, p50/p95/p99 latency, Kafka lag)
- [ ] Alertmanager runbooks: P1 (order stuck > 5 min), P2 (Kafka lag > 50K), P3 (pod crash loop)
- [ ] k6 load test: 2,000 RPS peak; validate all p99 SLAs hold
- [ ] **Chaos Mesh** game day #1: kill 1 ScyllaDB node mid-wave; verify FC continues operating
- [ ] Synthetic monitoring: Grafana k6 scripted order flow every 60s in staging
- [ ] Distributed tracing end-to-end: single order trace visible in Jaeger from Kong → Carrier

### 6.5 Phase 2 Exit Criteria
- [ ] Wave generation completes in < 2 min for 10K allocated orders
- [ ] Sortation lane assignment latency < 100ms
- [ ] Carrier exception detected and `ShipmentDelayed` event fires within 2 min of threshold
- [ ] System sustains 2,000 RPS for 60 min in load test; no data loss
- [ ] Chaos game day: ScyllaDB single-node failure; zero order loss; automatic recovery

---

## 7. Phase 3 — Post-Ship & Returns (Weeks 25–32)

> **Goal:** Returns intake, condition routing, refund triggers, and full analytics pipeline.

### 7.1 Sprint 11 (Weeks 25–26): Returns Service

**Post-Ship Squad**
- [ ] Scaffold `returns` service: Spring Boot 4
- [ ] `POST /returns/initiate` → generate prepaid return label (EasyPost SDK)
- [ ] ScyllaDB: `returns (return_id, order_id, sku_list, status, condition, routed_to)`
- [ ] Condition capture UI: React 18 tablet app; associate scans item barcode → selects condition
- [ ] Drools 10 rules engine: condition + category → `RESTOCK` / `QUARANTINE` / `LIQUIDATION`
- [ ] Publish `ReturnReceived` and `ReturnRouted` events to Kafka `returns` topic

---

### 7.2 Sprint 12 (Weeks 27–28): Returns → Inventory + Refund

**Post-Ship Squad + Inventory Squad**
- [ ] Inventory Service: consume `ReturnRestocked` → increment `on_hand` via ScyllaDB LWT
- [ ] Publish `InventoryAdjusted` event (sourced from return)
- [ ] Refund trigger: `ReturnAccepted` event → external OMS webhook
- [ ] Return processing SLA: restock within 48h of receive; alert if breached

---

### 7.3 Sprint 13 (Weeks 29–30): Analytics Pipeline

**Post-Ship Squad + Platform**
- [ ] Kafka → MinIO pipeline (Kafka Connect S3 sink → MinIO buckets)
- [ ] Apache Trino cluster: connect to MinIO; catalog for `orders`, `shipments`, `returns`, `tracking`
- [ ] Apache Superset: dashboards — return rate by SKU, reason code, carrier on-time rate
- [ ] Audit log pipeline: domain audit events → MinIO WORM bucket (object lock enabled)

---

### 7.4 Sprint 14 (Weeks 31–32): Carrier Manifest + EDI Hardening

**Carrier Squad**
- [ ] Apache Camel 4: inbound EDI X12 856 ASN ingestion (supplier advance ship notices)
- [ ] EDI X12 850 outbound PO (stub for Replenishment, real in Phase 4)
- [ ] Dead-letter handling for EDI parse failures → NATS alert + manual review queue
- [ ] Contract tests: Pact for all carrier webhook schemas
- [ ] End-to-end returns demo: customer initiates return → scanned → restocked → refund triggered

### 7.5 Phase 3 Exit Criteria
- [ ] Returns processed in < 48h (receive → restock) in staging
- [ ] Refund event fires within 60s of `ReturnAccepted`
- [ ] Trino query: return rate by SKU returns in < 5s on 100M row dataset
- [ ] MinIO audit log: immutable; tamper-evident

---

## 8. Phase 4 — Replenishment (Weeks 33–40)

> **Goal:** Reactive + predictive inventory replenishment; fully automated PO/transfer order generation.

### 8.1 Sprint 15 (Weeks 33–34): Reactive Replenishment

**Replenishment Squad**
- [ ] Scaffold `replenishment` service: Python 3.12 + FastAPI
- [ ] Apache Flink 1.20 job (K8s Flink Operator): consume `inventory-events` → maintain per-SKU rolling demand state
- [ ] Reorder point calculation: $\bar{d} \times L + z \times \sigma_d \times \sqrt{L}$ (from live demand window)
- [ ] Trigger `ReplenishmentRequired` event when `available < reorder_point`
- [ ] ScyllaDB: `replenishment_triggers (fc_id, sku, triggered_at, status, po_id)`

---

### 8.2 Sprint 16 (Weeks 35–36): PO Generation + EDI

**Replenishment Squad**
- [ ] PO generation: `ReplenishmentRequired` → create PO in ScyllaDB → Apache Camel X12 850 → supplier EDI
- [ ] Transfer order: if adjacent FC has stock → `TransferOrderCreated` event → FC Transfer Service
- [ ] ASN ingestion: supplier sends X12 856 ASN → Camel parser → `ASNReceived` event → dock scheduling
- [ ] PostgreSQL: `purchase_orders`, `transfer_orders`, `asns` (relational, join-heavy reporting)

---

### 8.3 Sprint 17 (Weeks 37–38): Putaway + Slotting

**Replenishment Squad**
- [ ] Inbound receipt: scan item → confirm quantity → trigger putaway task
- [ ] Slotting rules engine (PostgreSQL): velocity class (A/B/C) → bin zone assignment
- [ ] Putaway task queue (ScyllaDB): assign task to associate; confirm via scan
- [ ] `PutawayCompleted` event → Inventory Service updates `on_hand`
- [ ] Apache Airflow: monthly slotting optimizer job (Python + SciPy); re-slots slow movers

---

### 8.4 Sprint 18 (Weeks 39–40): Predictive Replenishment (ML)

**Replenishment Squad**
- [ ] MLflow 2.x: train DeepAR time-series model on historical `inventory-events` + order data (stored in MinIO)
- [ ] Ray Serve: deploy DeepAR model as REST inference endpoint
- [ ] Flink job: call Ray Serve every 6h per SKU → update forecast in ScyllaDB
- [ ] Replenishment Service: use forecast to pre-compute reorder points before reactive threshold hit
- [ ] Apache Superset dashboard: forecast vs. actual demand; PO pipeline; in-transit inventory

### 8.5 Phase 4 Exit Criteria
- [ ] Reactive replenishment fires within 30s of crossing reorder point
- [ ] EDI X12 850 PO transmitted to supplier test endpoint; ACK received
- [ ] Putaway task completion updates `on_hand` in < 5s
- [ ] ML forecast MAPE (Mean Absolute Percentage Error) < 15% on holdout SKUs

---

## 9. Phase 5 — Scale & Hardening (Weeks 41–48)

> **Goal:** Multi-FC cell rollout, peak-load validation, security audit, and production readiness.

### 9.1 Sprint 19–20 (Weeks 41–44): Multi-FC Cell Rollout

- [ ] Deploy FC Cell B (second independent Kubernetes cluster + Kafka + ScyllaDB)
- [ ] Kafka MirrorMaker 2: replicate `orders` and `inventory-events` to regional Kafka
- [ ] FC Router: real routing decisions across 2 live FC cells
- [ ] ScyllaDB: cross-FC replication for global inventory view (separate keyspace, eventual consistency)
- [ ] Test: regional Kafka failure → FC Cell A continues operating independently; orders processed locally
- [ ] Test: FC Cell A ScyllaDB node failure → Cassandra-style gossip auto-recovery; zero data loss

---

### 9.2 Sprint 21 (Weeks 45–46): Peak Load + Chaos

- [ ] k6 load test: **10,000 RPS** (peak day simulation); 2h sustained; validate all SLAs
- [ ] KEDA autoscaling validation: Kafka lag metric → auto-scale consumer pods within 60s
- [ ] Chaos Mesh game day #2:
  - Kill Kafka broker mid-wave → MirrorMaker 2 failover; consumers reconnect
  - Kill Valkey primary → replica promotion; reservation cache hit rate recovers in < 30s
  - Network partition between FC cells → both cells continue locally
- [ ] Trino query performance: 100M row analytics queries < 10s p95
- [ ] ScyllaDB: validate 1M+ writes/sec across 9-node cluster (3 per rack)

---

### 9.3 Sprint 22 (Weeks 47–48): Security Audit + Production Readiness

**Security**
- [ ] Trivy: zero Critical/High CVEs in all Harbor images
- [ ] OPA policy review: RBAC correct for all service accounts
- [ ] Vault Transit key rotation test: re-encrypt ScyllaDB field-level PII; zero downtime
- [ ] SPIFFE/SPIRE: verify all service-to-service calls carry valid SVIDs; reject unknown workloads
- [ ] Penetration test: Kong Gateway rate limiting, auth bypass, injection (external vendor)
- [ ] MinIO WORM audit bucket: verify object lock prevents deletion/overwrite

**Operational Readiness**
- [ ] Runbook for every P1 Alertmanager rule (< 15 runbooks total)
- [ ] On-call rotation established; PagerDuty escalation policies set
- [ ] Grafana SLO dashboards: error budget burn rate visible for each domain
- [ ] DR drill: full `staging` cluster restore from MinIO backups; RTO < 4h

### 9.4 Phase 5 Exit Criteria
- [ ] System sustains 10,000 RPS for 2h; all p99 SLAs met
- [ ] 2 FC cells operating independently; routing active
- [ ] Zero Critical CVEs in production images
- [ ] DR drill: RTO < 4h validated
- [ ] All P1 runbooks reviewed and approved by on-call team

---

## 10. Dependency Graph (Critical Path)

```
Phase 0: Platform Infra
    │
    ├─── Order Ingestion (Sprint 1)
    │         │
    │    Inventory Service (Sprint 1) ◄─── OrderReceived event
    │         │
    │    FC Router (Sprint 2)
    │         │
    │    Wave Planner (Sprint 3) ◄─── InventoryReserved event
    │         │
    │    Pick Engine (Sprint 4) ◄─── WaveReleased + PickList events
    │         │
    │    Pack Service (Sprint 5) ◄─── PickCompleted event
    │         │
    │    Carrier & Tracking (Sprint 6) ◄─── OrderPacked event
    │         │
    │    [Phase 1 Complete: End-to-End Walking Skeleton]
    │
    ├─── Sortation (Sprint 7) ◄─── OrderPacked event
    │
    ├─── Returns (Sprint 11) ◄─── OrderDelivered event
    │         │
    │    Inventory (returns restock path)
    │
    └─── Replenishment (Sprint 15) ◄─── inventory-events stream
              │
         Flink jobs (Sprint 15)
              │
         ML Forecasting (Sprint 18)
```

---

## 11. Risk Register

| # | Risk | Probability | Impact | Mitigation |
|---|------|------------|--------|-----------|
| R1 | ScyllaDB LWT performance degrades under high contention | Medium | High | Benchmark LWT at 2K RPS in Phase 0; fallback to Valkey Lua only if needed |
| R2 | Kafka MirrorMaker 2 lag during FC failover | Low | High | Test failover in Phase 5; set lag alert threshold at 10K messages |
| R3 | Vocollect device integration delays (hardware procurement) | High | Medium | Mock Vocollect server in dev; real integration in Phase 2 only |
| R4 | Apache Camel EDI X12 compliance gaps (carrier-specific) | Medium | High | Engage EDI specialist contractor in Phase 1 Sprint 6 |
| R5 | MLflow + Ray Serve model accuracy insufficient | Medium | Medium | Phase 4 starts with reactive only; ML is additive; define MAPE threshold early |
| R6 | Temporal workflow engine learning curve | Medium | Medium | Spike in Phase 0 Week 4; evaluate vs. simpler Kafka saga before committing |
| R7 | OPC-UA conveyor PLC integration (vendor-specific firmware) | High | Medium | PLC simulator (mock OPC-UA server) in dev; real hardware in Phase 2 UAT |
| R8 | Platform team bottleneck for infra changes | High | High | Every squad has read/write access to their own Helm chart; platform owns cluster-wide only |
| R9 | k6 load test reveals ScyllaDB hotspot at peak | Low | High | Token-aware routing + Valkey write-through cache; identified in Phase 2 chaos tests |
| R10 | Feature flag (Unleash) dependency for dark-launch | Low | Low | Unleash is simple; fallback to env-var flags if outage |

---

## 12. Definition of Done (Per Story)

- [ ] Code reviewed by ≥ 1 peer from same squad
- [ ] Unit test coverage ≥ 80% for new code
- [ ] Integration test with Testcontainers (Kafka + ScyllaDB + Valkey) passes in CI
- [ ] Pact contract published/verified for all new Kafka events or REST APIs consumed by other squads
- [ ] Avro schema registered in Confluent Schema Registry OSS; backward compatible
- [ ] OpenTelemetry span added for all DB and Kafka calls
- [ ] Prometheus metric added for all new business operations (counters + histograms)
- [ ] Helm chart updated; ArgoCD syncs to `staging` without manual intervention
- [ ] Unleash feature flag added if story is user-visible
- [ ] Runbook updated if story introduces new failure mode

---

## 13. Key Milestones

| Milestone | Target Date | Success Criteria |
|-----------|------------|-----------------|
| **M0: Platform Ready** | Week 4 | All infra deployed; CI/CD green; shared libs published |
| **M1: Walking Skeleton** | Week 16 | Single order flows end-to-end in staging |
| **M2: Ops Intelligence** | Week 24 | Wave optimization live; 2K RPS load test passes |
| **M3: Returns Live** | Week 32 | Returns processed; analytics pipeline ingesting |
| **M4: Replenishment Live** | Week 40 | Reactive + ML replenishment running in staging |
| **M5: Production Ready** | Week 48 | Multi-FC, 10K RPS, DR drill, security audit complete |

---

*This plan is a living document. Update sprint assignments and risk statuses at each sprint retrospective.*
