package com.shipping.pick.domain.event;

/**
 * Raised when every item in a pick list has been confirmed (all
 * {@link com.shipping.pick.domain.model.PickTask}s are in PICKED status).
 * <p>
 * The command handler maps this to the Avro {@code PickCompleted} record and
 * publishes it to the {@code pick-events} Kafka topic so Pack Service can
 * begin its workflow.
 */
public record PickListCompletedEvent(
        String pickListId,
        String orderId,
        String fcId) implements PickDomainEvent {}
