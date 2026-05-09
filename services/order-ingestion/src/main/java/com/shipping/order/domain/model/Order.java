package com.shipping.order.domain.model;

import com.shipping.cqrs.AggregateResult;
import com.shipping.order.domain.event.OrderDomainEvent;
import com.shipping.order.domain.event.OrderReceivedEvent;
import com.shipping.order.domain.event.OrderRoutedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root.
 * <p>
 * Immutable record — all lifecycle transitions return a new instance plus a
 * domain event via {@link AggregateResult}.  The command handler is
 * responsible for:
 * <ol>
 *   <li>Calling the appropriate domain method.</li>
 *   <li>Persisting {@code result.state()} via {@link com.shipping.order.infrastructure.persistence.OrderRepository}.</li>
 *   <li>Publishing each event in {@code result.events()} to Kafka.</li>
 * </ol>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   RECEIVED → ROUTED → RESERVED → IN_WAVE → PICKED → PACKED → SHIPPED
 *                    ↘ CANCELLED (saga compensation at any stage)
 * </pre>
 */
public record Order(
        UUID orderId,
        String idempotencyKey,
        String customerId,
        List<OrderItem> items,
        Address shippingAddress,
        String requestedDeliveryDate,   // ISO-8601 date string, nullable
        Status status,
        String fcId,                    // null until ROUTED
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        RECEIVED, ROUTED, RESERVED, IN_WAVE, PICKED, PACKED, SHIPPED, CANCELLED
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a freshly received order with a new random {@code orderId}.
     * Callers must immediately invoke {@link #receive()} on the returned
     * instance to obtain the {@link OrderReceivedEvent}.
     */
    public static Order newOrder(String idempotencyKey,
                                 String customerId,
                                 List<OrderItem> items,
                                 Address shippingAddress,
                                 String requestedDeliveryDate) {
        Instant now = Instant.now();
        return new Order(UUID.randomUUID(), idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, Status.RECEIVED, null, now, now);
    }

    // -------------------------------------------------------------------------
    // Domain methods — enforce invariants; return (new state + domain event)
    // -------------------------------------------------------------------------

    /**
     * Acknowledges that the order has been durably accepted.
     * <p>
     * Invariant: order must be freshly constructed (status == RECEIVED).
     *
     * @return aggregate result carrying this order and an {@link OrderReceivedEvent}
     */
    public AggregateResult<Order, OrderDomainEvent> receive() {
        requireStatus(Status.RECEIVED, "receive");
        return AggregateResult.of(this,
                new OrderReceivedEvent(orderId, idempotencyKey, customerId,
                        items, shippingAddress, requestedDeliveryDate));
    }

    /**
     * Assigns a fulfilment centre; advances status to {@code ROUTED}.
     * <p>
     * Invariant: order must be in {@code RECEIVED} status.
     *
     * @param fcId fulfilment centre identifier assigned by the FC router
     * @return aggregate result carrying the routed order and an {@link OrderRoutedEvent}
     */
    public AggregateResult<Order, OrderDomainEvent> route(String fcId) {
        requireStatus(Status.RECEIVED, "route");
        if (fcId == null || fcId.isBlank()) {
            throw new IllegalArgumentException("fcId must not be blank");
        }
        Order next = new Order(orderId, idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, Status.ROUTED, fcId,
                createdAt, Instant.now());
        return AggregateResult.of(next, new OrderRoutedEvent(orderId, fcId));
    }

    // -------------------------------------------------------------------------
    // Low-level wither methods (infrastructure use only — reconstitution)
    // -------------------------------------------------------------------------

    /** Infrastructure-only: advance status without raising a domain event. */
    public Order withStatus(Status status) {
        return new Order(orderId, idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, status, fcId, createdAt, Instant.now());
    }

    /** Infrastructure-only: assign FC without raising a domain event. */
    public Order withFcId(String fcId) {
        return new Order(orderId, idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, status, fcId, createdAt, Instant.now());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireStatus(Status expected, String operation) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Cannot perform '%s' on order %s in status %s (expected %s)"
                            .formatted(operation, orderId, this.status, expected));
        }
    }
}
