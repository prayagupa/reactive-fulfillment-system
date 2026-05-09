package com.shipping.order.domain.model;

/** A single line item inside an {@link Order}. Immutable record. */
public record OrderItem(String sku, int quantity, double unitPrice) {}
