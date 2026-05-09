# Pack Service

A reactive Clojure service that handles package labelling and shipment confirmation in the warehouse fulfilment pipeline. It consumes `PickCompleted` events, processes each package through a concurrent shipping pipeline, and emits shipment confirmations.

## What it does

- Reads inbound package events from stdin or a file of input events
- Fans work out to **8 concurrent worker threads** via `core.async` channels
- Each worker simulates label generation / carrier hand-off and confirms the shipment
- An aggregator thread collects confirmations and writes them to stdout

## Where this fits in the pipeline

```
PickCompleted event
      │
      ▼
 Pack Service  ──► OrderPacked event ──► Carrier & Tracking Service
```

See [Phase 1, Sprint 5](../../docs/IMPLEMENTATION-PLAN.md) in the implementation plan for the full Pack Service delivery scope including 3D bin-packing, ZPL label generation, EasyPost integration, and the Pack Station UI.

## Documentation

| Document | Description |
|----------|-------------|
| [PRD — Fulfillment System](../../docs/PRD-fulfillment-system.md) | Product requirements |
| [SDS — Fulfillment System](../../docs/SDS-fulfillment-system.md) | System design specification |
| [Implementation Plan](../../docs/IMPLEMENTATION-PLAN.md) | Phased delivery plan |
| [Initial Thoughts](../../docs/initial-thoughts.md) | Early design notes |

## Project structure

```
services/pack-service/
├── project.clj                          # Leiningen build & dependencies
├── src/
│   └── reactive_shipping/
│       ├── core.clj                     # Entry point & mode dispatch
│       └── shipping.clj                 # core.async pipeline (channels, workers, aggregator)
└── test/
    └── reactive_shipping/
        └── core_test.clj
```

## Usage

- Install `lein`
- Optionally configure `~/.lein/profiles.clj` for proxy / private repositories

```
;; Read each package from stdin


lein run inline-reactive
(inline)
mypackage1
mypackage1 [consumer confirms] shipment

;; or
lein run inline-reactive < input-events > output-events

```

```
;; read from input-events filestream
$ lein run default-reactive < input-events
(async)
package-1 shipped
package-2 shipped
package-4 shipped
package-7 shipped
package-5 shipped
package-6 shipped
package-3 shipped
package-8 shipped
package-13 shipped
package-9 shipped
package-14 shipped
package-15 shipped
package-11 shipped
package-16 shipped
...
```

## License

Copyright © upadhyay

##  References

[Chapter 9 - The Sacred Art of Concurrent and Parallel Programming](http://www.braveclojure.com/concurrency/)

[Chapter 11 - Mastering Concurrent Processes with core.async](http://www.braveclojure.com/core-async/)

[Using core.async for Producer-consumer Systems
](http://elbenshira.com/blog/using-core-async-for-producer-consumer-workflows/)
