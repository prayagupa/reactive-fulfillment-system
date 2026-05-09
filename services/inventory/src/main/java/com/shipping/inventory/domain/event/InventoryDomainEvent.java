package com.shipping.inventory.domain.event;

import com.shipping.cqrs.DomainEvent;

/**
 * Sealed base for all internal domain events raised by the
 * {@link com.shipping.inventory.domain.model.StockLedger} aggregate.
 */
public sealed interface InventoryDomainEvent extends DomainEvent
        permits InventoryReservedEvent, InventoryInsufficientEvent {}
