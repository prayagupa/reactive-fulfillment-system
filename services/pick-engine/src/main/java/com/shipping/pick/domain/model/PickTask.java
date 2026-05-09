package com.shipping.pick.domain.model;

/**
 * A single pick-list line item assigned to a warehouse associate.
 * <p>
 * Immutable record — status transitions return a new instance via
 * {@link #withStatus(Status)} and {@link #withPickedBy(String)}.
 */
public record PickTask(
        String pickListId,
        String orderId,
        int itemSeq,
        String sku,
        int quantityRequired,
        String binLocation,
        String pickedBy,
        Status status) {

    public enum Status { PENDING, ASSIGNED, PICKED, SHORT }

    /** Transition: advance the task status. */
    public PickTask withStatus(Status status) {
        return new PickTask(pickListId, orderId, itemSeq, sku,
                quantityRequired, binLocation, pickedBy, status);
    }

    /** Transition: assign the associate who performed the pick. */
    public PickTask withPickedBy(String pickedBy) {
        return new PickTask(pickListId, orderId, itemSeq, sku,
                quantityRequired, binLocation, pickedBy, status);
    }
}
