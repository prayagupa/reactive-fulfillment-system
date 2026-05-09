package com.shipping.inventory.domain.event;

/**
 * Raised by {@link com.shipping.inventory.domain.model.StockLedger#reserve}
 * when the requested quantity was successfully reserved.
 */
public record InventoryReservedEvent(
        String fcId,
        String sku,
        int quantityReserved) implements InventoryDomainEvent {}
