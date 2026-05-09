package com.shipping.carrier.domain.event;

import com.shipping.cqrs.DomainEvent;

/**
 * Sealed marker for all in-process domain events raised by the
 * {@code carrier-tracking} bounded context.
 */
public sealed interface CarrierDomainEvent extends DomainEvent
        permits ManifestTransmittedEvent {}
