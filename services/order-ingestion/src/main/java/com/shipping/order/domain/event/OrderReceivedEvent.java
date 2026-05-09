package com.shipping.order.domain.event;

import com.shipping.order.domain.model.Address;
import com.shipping.order.domain.model.OrderItem;

import java.util.List;
import java.util.UUID;

/**
 * Raised by {@link com.shipping.order.domain.model.Order#receive()} when a new
 * order has been accepted and persisted.
 * <p>
 * The command handler maps this to the Avro {@code OrderReceived} record and
 * publishes it to the {@code orders} Kafka topic.
 */
public record OrderReceivedEvent(
        UUID orderId,
        String idempotencyKey,
        String customerId,
        List<OrderItem> items,
        Address shippingAddress,
        String requestedDeliveryDate) implements OrderDomainEvent {}
