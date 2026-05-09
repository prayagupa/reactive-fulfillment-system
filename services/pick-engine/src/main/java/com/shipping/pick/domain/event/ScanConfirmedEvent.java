package com.shipping.pick.domain.event;

/**
 * Raised when one item in a pick list has been successfully scanned and
 * confirmed — but the pick list is not yet fully complete.
 */
public record ScanConfirmedEvent(
        String pickListId,
        String orderId,
        int itemSeq,
        String sku) implements PickDomainEvent {}
