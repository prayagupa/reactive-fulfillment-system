package com.shipping.pick.domain.event;

/**
 * Raised when an associate reports that a pick item is short (not found in
 * the expected bin location).
 * <p>
 * The command handler publishes a Kafka event that routes to the Exception
 * Service and Wave Planner for re-routing.
 */
public record PickShortEvent(
        String pickListId,
        String orderId,
        int itemSeq,
        String sku) implements PickDomainEvent {}
