package com.shipping.pick.domain.event;

import com.shipping.cqrs.DomainEvent;

/**
 * Sealed base for all internal domain events raised by the
 * {@link com.shipping.pick.domain.model.PickList} aggregate.
 */
public sealed interface PickDomainEvent extends DomainEvent
        permits ScanConfirmedEvent, PickListCompletedEvent, PickShortEvent {}
