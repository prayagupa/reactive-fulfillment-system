package com.shipping.order.domain.event;

import java.util.UUID;

/**
 * Raised by {@link com.shipping.order.domain.model.Order#route(String)} when
 * the FC router has assigned a fulfilment centre to this order.
 * <p>
 * The command handler maps this to the Avro {@code OrderRouted} record and
 * publishes it to the {@code orders} Kafka topic.
 */
public record OrderRoutedEvent(UUID orderId, String fcId) implements OrderDomainEvent {}
