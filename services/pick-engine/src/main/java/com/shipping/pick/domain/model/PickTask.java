package com.shipping.pick.domain.model;

/**
 * A single pick-list line item assigned to a warehouse associate.
 * <p>
 * Entity within the {@link PickList} aggregate — never modified in isolation;
 * all transitions go through {@link PickList#confirmScan} or
 * {@link PickList#markShort}.
 * <p>
 * Immutable record — status transitions return a new instance via
 * {@link #withStatus(Status)} and {@link #withPickedBy(String)}.
 */
public record PickTask(
        String pickListId,
        String orderId,
        String fcId,
        int itemSeq,
        String sku,
        int quantityRequired,
        String binLocation,
        String pickedBy,
        Status status) {

    public enum Status { PENDING, ASSIGNED, PICKED, SHORT }

    /** Infrastructure-only: advance status. */
    public PickTask withStatus(Status status) {
        return new PickTask(pickListId, orderId, fcId, itemSeq, sku,
                quantityRequired, binLocation, pickedBy, status);
    }

    /** Infrastructure-only: assign the associate who performed the pick. */
    public PickTask withPickedBy(String pickedBy) {
        return new PickTask(pickListId, orderId, fcId, itemSeq, sku,
                quantityRequired, binLocation, pickedBy, status);
    }
}

