---
applyTo: "services/**/*.java,libs/**/*.java"
---

# Java implementation guidelines — reactive-shipping-system

## Records for DTOs
- **Always use Java `record` classes** for any DTO, request, or response type (classes whose sole purpose is to carry data across layer boundaries).
- Records belong in the `api/dto` package of the owning service.
- Nested DTOs (e.g. `ItemDto`, `AddressDto`) must also be records, declared as inner types of the outer record.
- Place bean-validation annotations (`@NotBlank`, `@NotNull`, `@Positive`, `@Valid`, etc.) directly on the record component parameter, not on a field.
- **Do NOT** create mutable JavaBean DTOs with private fields + getters/setters.

### Example
```java
// ✅ correct
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<ItemDto> items,
        @NotNull  @Valid AddressDto shippingAddress,
        String requestedDeliveryDate) {

    public record ItemDto(
            @NotBlank String sku,
            @Positive int quantity,
            @Positive double unitPrice) {}

    public record AddressDto(
            @NotBlank String line1,
            String line2,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank String postalCode,
            @NotBlank String countryCode) {}
}

public record CreateOrderResponse(String orderId, String status) {}

// ❌ wrong — do not generate
public class CreateOrderRequest {
    private String customerId;
    public String getCustomerId() { ... }
    public void setCustomerId(String v) { ... }
}
```

## Calling record accessors
- Access record components via the generated accessor method (`req.customerId()`, `item.sku()`, `addr.city()`), **not** via JavaBean convention (`getCustomerId()`).

## Domain models
- **Domain entities are also records.** Declare every domain model (e.g. `Order`, `PickTask`) as a `record`.
- Because records are immutable, **state transitions must return a new instance** via _wither_ methods (named `with<Field>(…)`).
- Place wither methods inside the record body.
- Factory methods (`newOrder(…)`, etc.) are allowed as `static` methods inside the record body.

### Example
```java
// ✅ correct — domain model as a record with wither methods
public record Order(
        UUID orderId,
        String customerId,
        List<OrderItem> items,
        Status status,
        String fcId,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status { RECEIVED, ROUTED, RESERVED, IN_WAVE, PICKED, PACKED, SHIPPED, CANCELLED }

    public static Order newOrder(String customerId, List<OrderItem> items) {
        Instant now = Instant.now();
        return new Order(UUID.randomUUID(), customerId, items, Status.RECEIVED, null, now, now);
    }

    public Order withStatus(Status status) {
        return new Order(orderId, customerId, items, status, fcId, createdAt, Instant.now());
    }

    public Order withFcId(String fcId) {
        return new Order(orderId, customerId, items, status, fcId, createdAt, Instant.now());
    }
}

// ❌ wrong — do not generate mutable domain entities
public class Order {
    private Status status;
    public void setStatus(Status s) { this.status = s; }
}
```

- In repositories, reconstruct domain records using their canonical constructor (all fields provided); **do not use setters**.
- In services, reassign the local variable when applying a wither: `task = task.withStatus(PICKED);`

## CQRS (Command Query Responsibility Segregation)
Every service uses an in-process CQRS model. **Do not put both read and write logic in the same class.**

### Shared library
The four CQRS interfaces live in **`libs/common-cqrs`** (`com.shipping.cqrs` package):
- `Command` — write-side marker
- `Query<R>` — read-side marker with return type
- `CommandHandler<C, R>` — `@FunctionalInterface`, one per command type
- `QueryHandler<Q, R>` — `@FunctionalInterface`, one per query type

Add `implementation(project(":libs:common-cqrs"))` to the service `build.gradle.kts`.
**Do NOT** copy or re-declare these interfaces inside a service's own packages.

### Package structure (per service)
```
application/
  command/
    XxxCommand.java        ← record implements com.shipping.cqrs.Command
    XxxCommandHandler.java ← @Service implements CommandHandler<XxxCommand, R>
  query/
    XxxQuery.java          ← record implements com.shipping.cqrs.Query<R>
    XxxQueryHandler.java   ← @Service implements QueryHandler<XxxQuery, R>
    XxxResult.java         ← read-model record (if needed)
```

### Rules
- **Commands** (write side): one command per intent (e.g. `CreateOrderCommand`, `ConfirmScanCommand`). Command records are immutable and hold exactly the data needed to execute the intent.
- **Queries** (read side): one query per read concern (e.g. `GetOrderQuery`, `GetStockQuery`). Query records carry only the lookup keys. Return a **read-model record** (`XxxResult`, `XxxView`) — never return a mutable domain entity from a query handler.
- **Command handlers** may publish Kafka events and mutate state (via repository). They must **not** return domain data beyond a minimal acknowledgement.
- **Query handlers** must **not** publish events or mutate state.
- Controllers and Kafka consumers inject specific handlers directly — **no `*Service` god-class**.
- Old `*Service` classes that have been split are marked `@Deprecated(forRemoval = true)` and left empty.

### Example
```java
// Command record
public record CreateOrderCommand(String idempotencyKey, CreateOrderRequest request)
        implements com.shipping.cqrs.Command {}

// Command handler
@Service
public class CreateOrderCommandHandler
        implements CommandHandler<CreateOrderCommand, Mono<CreateOrderResponse>> {

    @Override
    public Mono<CreateOrderResponse> handle(CreateOrderCommand cmd) { ... }
}

// Query record
public record GetOrderQuery(String orderId) implements Query<CreateOrderResponse> {}

// Query handler
@Service
public class GetOrderQueryHandler
        implements QueryHandler<GetOrderQuery, Mono<CreateOrderResponse>> {

    @Override
    public Mono<CreateOrderResponse> handle(GetOrderQuery query) { ... }
}

// Controller — injects handlers directly, no service layer
@RestController
public class OrderController {
    public OrderController(CreateOrderCommandHandler createOrderHandler,
                           GetOrderQueryHandler getOrderHandler) { ... }
}
```

## Reactive style
- Use `Mono<T>` / `Flux<T>` (Project Reactor) in service and controller layers.
- Avoid blocking calls inside reactive chains.

## Dependency injection
- Prefer constructor injection; do not use field injection (`@Autowired` on a field).

## Java version
- Target Java 21 (`--release 21`). Use records, sealed interfaces, pattern matching, text blocks, and switch expressions where appropriate.
