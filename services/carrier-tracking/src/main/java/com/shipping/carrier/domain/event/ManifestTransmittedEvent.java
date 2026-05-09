package com.shipping.carrier.domain.event;

import com.shipping.cqrs.DomainEvent;

/**
 * Raised when the X12 856 manifest has been successfully transmitted
 * to the carrier via EDI.
 *
 * @param orderId        the order being shipped
 * @param shipmentId     warehouse shipment identifier
 * @param trackingNumber carrier-assigned tracking number
 * @param carrier        carrier code (e.g. "UPS", "FEDEX")
 */
public record ManifestTransmittedEvent(
        String orderId,
        String shipmentId,
        String trackingNumber,
        String carrier
) implements CarrierDomainEvent {}
