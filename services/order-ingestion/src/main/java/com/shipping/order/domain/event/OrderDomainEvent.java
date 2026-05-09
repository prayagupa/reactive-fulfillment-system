package com.shipping.order.domain.event;

import com.shipping.cqrs.DomainEvent;

/**
 * Sealed base for all internal domain events raised by the {@link com.shipping.order.domain.model.Order} aggregate.
 * <p>
 * These are <em>in-process</em> events; they are <strong>not</strong> the Avro records sent to Kafka.
 * The command handler maps each to its Avro counterpart before publishing.
 */
public sealed interface OrderDomainEvent extends DomainEvent
        permits OrderReceivedEvent, OrderRoutedEvent {}
