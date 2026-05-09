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

## Reactive style
- Use `Mono<T>` / `Flux<T>` (Project Reactor) in service and controller layers.
- Avoid blocking calls inside reactive chains.

## Dependency injection
- Prefer constructor injection; do not use field injection (`@Autowired` on a field).

## Java version
- Target Java 25 (`--release 25`). Use records, sealed interfaces, pattern matching, text blocks, and switch expressions where appropriate.
