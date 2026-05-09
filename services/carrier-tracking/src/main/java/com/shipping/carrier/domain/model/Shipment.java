package com.shipping.carrier.domain.model;

import com.shipping.carrier.domain.event.CarrierDomainEvent;
import com.shipping.carrier.domain.event.ManifestTransmittedEvent;
import com.shipping.cqrs.AggregateResult;

/**
 * Aggregate root for a carrier shipment.
 *
 * <p>Encapsulates the EDI manifest transmission decision.  The aggregate
 * deliberately has no persistence state of its own — it is reconstituted
 * from the {@link com.shipping.events.OrderPacked} Avro event that drives
 * the command, and its only mutation is the transition to
 * {@code Status.MANIFESTED}.
 *
 * @param orderId        the originating order
 * @param shipmentId     warehouse-assigned shipment ID
 * @param trackingNumber carrier-assigned tracking number
 * @param carrier        carrier code (e.g. "UPS")
 * @param status         current manifest status
 */
public record Shipment(
        String orderId,
        String shipmentId,
        String trackingNumber,
        String carrier,
        Status status
) {

    public enum Status { PENDING, MANIFESTED }

    /**
     * Factory — reconstitutes a shipment from an inbound
     * {@link com.shipping.events.OrderPacked} Avro event before the manifest
     * has been transmitted.
     */
    public static Shipment pending(String orderId, String shipmentId,
                                   String trackingNumber, String carrier) {
        return new Shipment(orderId, shipmentId, trackingNumber, carrier, Status.PENDING);
    }

    /**
     * Domain method: transmit the manifest.
     *
     * <p>Business rule: a shipment may only be manifested once.
     *
     * @return an {@link AggregateResult} containing the updated aggregate
     *         (status → {@code MANIFESTED}) and a {@link ManifestTransmittedEvent}
     * @throws IllegalStateException if the manifest has already been transmitted
     */
    public AggregateResult<Shipment, CarrierDomainEvent> transmitManifest() {
        if (status == Status.MANIFESTED) {
            throw new IllegalStateException(
                "Manifest already transmitted for shipment " + shipmentId);
        }
        Shipment updated = new Shipment(orderId, shipmentId, trackingNumber, carrier,
                                        Status.MANIFESTED);
        ManifestTransmittedEvent event = new ManifestTransmittedEvent(
                orderId, shipmentId, trackingNumber, carrier);
        return AggregateResult.of(updated, event);
    }
}
