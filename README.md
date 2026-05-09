# Reactive Shipping System

A reactive, event-driven warehouse fulfilment platform. Orders are ingested, routed to the optimal fulfilment centre, picked, packed, labelled, and handed off to carriers — all driven by Kafka events and asynchronous microservices.

## Documentation

| Document | Description |
|----------|-------------|
| [PRD](docs/PRD-fulfillment-system.md) | Product requirements and business context |
| [SDS](docs/SDS-fulfillment-system.md) | System design — architecture, data models, event contracts |
| [Implementation Plan](docs/IMPLEMENTATION-PLAN.md) | 48-week phased delivery plan across 10 squads |
| [Initial Thoughts](docs/initial-thoughts.md) | Early design notes and trade-offs |

## High-level architecture

```
Customer / OMS
      │
      ▼
 Kong Gateway
      │
      ▼
Order Ingestion ──► FC Router ──► Inventory Service
                                        │
                                   Wave Planner
                                        │
                                   Pick Engine
                                        │
                                  Pack Service ◄── (this repo, Sprint 5)
                                        │
                              Carrier & Tracking
                                        │
                                  [OrderShipped]
```

## Services

| Service | Location | Language | Status |
|---------|----------|----------|--------|
| Pack Service | [services/pack-service](services/pack-service/) | Clojure | 🟡 In progress |

More services will be added here as each squad scaffolds their domain (see [Implementation Plan](docs/IMPLEMENTATION-PLAN.md)).

## Tech stack

| Concern | Technology |
|---------|-----------|
| Messaging | Apache Kafka 3.8 (KRaft) + Confluent Schema Registry |
| Primary DB | ScyllaDB 6 (wide-row, low-latency) |
| Cache | Valkey 8 (Redis-compatible) |
| Search | OpenSearch 2.x |
| Analytics | Apache Trino + MinIO |
| Service mesh | Istio 1.23 (mTLS) |
| API gateway | Kong Gateway OSS |
| Observability | Prometheus 3 + Grafana 11 + Jaeger 2 + OpenTelemetry |
| CI/CD | GitHub Actions + ArgoCD + Harbor |
| Secrets | HashiCorp Vault |
| Feature flags | Unleash |
