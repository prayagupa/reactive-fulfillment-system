package com.shipping.order.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core domain model for an order.
 * Persisted to ScyllaDB table {@code orders} (single-table design, fc_id as partition key).
 */
public class Order {

    public enum Status {
        RECEIVED, ROUTED, RESERVED, IN_WAVE, PICKED, PACKED, SHIPPED, CANCELLED
    }

    private UUID orderId;
    private String idempotencyKey;
    private String customerId;
    private List<OrderItem> items;
    private Address shippingAddress;
    private String requestedDeliveryDate;   // ISO-8601 date, nullable
    private Status status;
    private String fcId;                    // assigned by fc-router, nullable until routed
    private Instant createdAt;
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Order() {}

    public static Order newOrder(String idempotencyKey,
                                 String customerId,
                                 List<OrderItem> items,
                                 Address shippingAddress,
                                 String requestedDeliveryDate) {
        Order o = new Order();
        o.orderId = UUID.randomUUID();
        o.idempotencyKey = idempotencyKey;
        o.customerId = customerId;
        o.items = items;
        o.shippingAddress = shippingAddress;
        o.requestedDeliveryDate = requestedDeliveryDate;
        o.status = Status.RECEIVED;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(String requestedDeliveryDate) { this.requestedDeliveryDate = requestedDeliveryDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getFcId() { return fcId; }
    public void setFcId(String fcId) { this.fcId = fcId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
