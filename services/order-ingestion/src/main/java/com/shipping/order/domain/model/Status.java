package com.shipping.order.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core domain model for an order.
 * Persisted to ScyllaDB table {@code orders} (single-table design, fc_id as partition key).
 * <p>
 * Immutable record — lifecycle state transitions return a new instance via
 * {@link #withStatus(Status)} and {@link #withFcId(String)}.
 */
public record Order(
        UUID orderId,
        String idempotencyKey,
        String customerId,
        List<OrderItem> items,
        Address shippingAddress,
        String requestedDeliveryDate,   // ISO-8601 date, nullable
        Status status,
        String fcId,                    // assigned by fc-router, null until routed
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        RECEIVED, ROUTED, RESERVED, IN_WAVE, PICKED, PACKED, SHIPPED, CANCELLED
    }

    /** Factory: creates a freshly received order with a new random {@code orderId}. */
    public static Order newOrder(String idempotencyKey,
                                 String customerId,
                                 List<OrderItem> items,
                                 Address shippingAddress,
                                 String requestedDeliveryDate) {
        Instant now = Instant.now();
        return new Order(UUID.randomUUID(), idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, Status.RECEIVED, null, now, now);
    }

    /** Transition: advance the lifecycle status (sets {@code updatedAt} to now). */
    public Order withStatus(Status status) {
        return new Order(orderId, idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, status, fcId, createdAt, Instant.now());
    }

    /** Transition: assign a fulfilment centre (sets {@code updatedAt} to now). */
    public Order withFcId(String fcId) {
        return new Order(orderId, idempotencyKey, customerId, items,
                shippingAddress, requestedDeliveryDate, status, fcId, createdAt, Instant.now());
    }
}
