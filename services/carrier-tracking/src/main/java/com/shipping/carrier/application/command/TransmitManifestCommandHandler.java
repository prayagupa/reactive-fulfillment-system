package com.shipping.carrier.application.command;

import com.shipping.carrier.domain.event.ManifestTransmittedEvent;
import com.shipping.carrier.domain.model.Shipment;
import com.shipping.cqrs.AggregateResult;
import com.shipping.cqrs.CommandHandler;
import com.shipping.carrier.domain.event.CarrierDomainEvent;
import com.shipping.events.OrderPacked;
import com.shipping.events.OrderShipped;
import com.shipping.events.TrackingEvent;
import com.shipping.events.TrackingStatus;
import com.shipping.kafka.producer.DomainEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * CQRS write side: handles {@link TransmitManifestCommand}.
 *
 * <p>Four-step thin coordinator:
 * <ol>
 *   <li>Load — reconstitute {@link Shipment} aggregate from the inbound Avro event</li>
 *   <li>Decide — call {@code shipment.transmitManifest()} to get domain events</li>
 *   <li>Save — transmit EDI via Camel (the only side-effect on success)</li>
 *   <li>Publish — map {@link ManifestTransmittedEvent} → Avro {@code OrderShipped}
 *       + {@code TrackingEvent} and publish to Kafka</li>
 * </ol>
 */
@Service
public class TransmitManifestCommandHandler
        implements CommandHandler<TransmitManifestCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(TransmitManifestCommandHandler.class);
    private static final String TRACKING_TOPIC = "tracking-events";

    private final DomainEventPublisher publisher;
    private final ProducerTemplate camelProducer;
    private final MeterRegistry meterRegistry;

    public TransmitManifestCommandHandler(DomainEventPublisher publisher,
                                          ProducerTemplate camelProducer,
                                          MeterRegistry meterRegistry) {
        this.publisher = publisher;
        this.camelProducer = camelProducer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Void handle(TransmitManifestCommand cmd) {
        OrderPacked packed = cmd.orderPacked();

        // ── Step 1: Load ─────────────────────────────────────────────────────
        Shipment shipment = Shipment.pending(
            packed.getOrderId().toString(),
            packed.getShipmentId().toString(),
            packed.getTrackingNumber().toString(),
            packed.getCarrier().toString()
        );

        // ── Step 2: Domain decision ───────────────────────────────────────────
        AggregateResult<Shipment, CarrierDomainEvent> result = shipment.transmitManifest();

        // ── Step 3: Save (EDI transmission is the external side-effect) ───────
        String ediBody = buildX12_856(packed);
        camelProducer.sendBodyAndHeaders("direct:transmit-manifest", ediBody,
            Map.of("shipmentId", packed.getShipmentId()));

        // ── Step 4: Publish ───────────────────────────────────────────────────
        result.events().forEach(e -> {
            switch (e) {
                case ManifestTransmittedEvent ev -> publishShipped(ev);
            }
        });

        meterRegistry.counter("carrier.manifest.transmitted").increment();
        log.info("Manifest transmitted orderId={}", packed.getOrderId());
        return null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void publishShipped(ManifestTransmittedEvent ev) {
        OrderShipped shipped = OrderShipped.newBuilder()
            .setOrderId(ev.orderId())
            .setShipmentId(ev.shipmentId())
            .setTrackingNumber(ev.trackingNumber())
            .setCarrier(ev.carrier())
            .setEstimatedDeliveryDate(null)
            .setEventTime(Instant.now())
            .build();
        publisher.publish(TRACKING_TOPIC, ev.orderId(), shipped);

        TrackingEvent tracking = TrackingEvent.newBuilder()
            .setTrackingNumber(ev.trackingNumber())
            .setCarrier(ev.carrier())
            .setStatus(TrackingStatus.IN_TRANSIT)
            .setLocation(null)
            .setDescription("Shipment picked up from FC")
            .setOccurredAt(Instant.now())
            .setEventTime(Instant.now())
            .build();
        publisher.publish(TRACKING_TOPIC, ev.trackingNumber(), tracking);
    }

    /** Builds a minimal X12 856 Ship Notice. Production uses a proper EDI library. */
    private String buildX12_856(OrderPacked event) {
        String date = Instant.now().toString().substring(2, 8).replace("-", "");
        String time = Instant.now().toString().substring(11, 16).replace(":", "");
        return String.format(
            "ISA*00*          *00*          *ZZ*SHIPPER        *ZZ*CARRIER        *%s*%s*^*00501*000000001*0*P*:~\n" +
            "GS*SH*SHIPPER*CARRIER*%s*%s*1*X*005010X158A1~\n" +
            "ST*856*0001~\n" +
            "BSN*00*%s*%s*%s~\n" +
            "HL*1**S~\n" +
            "TD5**2*%s**%s~\n" +
            "SE*6*0001~\n" +
            "GE*1*1~\n" +
            "IEA*1*000000001~\n",
            date, time, date, time,
            event.getShipmentId(),
            Instant.now().toString().substring(0, 10).replace("-", ""),
            time,
            event.getCarrier(),
            event.getTrackingNumber()
        );
    }
}
