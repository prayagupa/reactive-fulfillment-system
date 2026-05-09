package com.shipping.inventory.domain.event;

/**
 * Raised by {@link com.shipping.inventory.domain.model.StockLedger#reserve}
 * when there is insufficient stock to satisfy the requested quantity.
 */
public record InventoryInsufficientEvent(
        String fcId,
        String sku,
        int requested,
        int available) implements InventoryDomainEvent {}
