package com.shipping.order.api.dto;

/** Outbound response for order creation / lookup. Immutable record. */
public record CreateOrderResponse(String orderId, String status) {}
