package com.shipping.carrier.application.command;

import com.shipping.cqrs.CommandHandler;
import com.shipping.carrier.application.query.GetTrackingQuery;
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
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Build an X12 856 EDI Ship Notice body</li>
 *   <li>Transmit via Camel → SFTP route</li>
 *   <li>Publish {@code OrderShipped} and initial {@code TrackingEvent} to Kafka</li>
 * </ol>
 */
@Service
public class TransmitManifestCommandHandler
        implements CommandHandler<TransmitManifestCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(TransmitManifestCommandHandler.class);

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
        OrderPacked event = cmd.orderPacked();
        String ediBody = buildX12_856(event);

        camelProducer.sendBodyAndHeaders("direct:transmit-manifest", ediBody,
            Map.of("shipmentId", event.getShipmentId()));

        OrderShipped shipped = OrderShipped.newBuilder()
            .setOrderId(event.getOrderId().toString())
            .setShipmentId(event.getShipmentId().toString())
            .setTrackingNumber(event.getTrackingNumber().toString())
            .setCarrier(event.getCarrier().toString())
            .setEstimatedDeliveryDate(null)
            .setEventTime(Instant.now().toEpochMilli())
            .build();
        publisher.publish("tracking-events", event.getOrderId().toString(), shipped);

        TrackingEvent tracking = TrackingEvent.newBuilder()
            .setTrackingNumber(event.getTrackingNumber().toString())
            .setCarrier(event.getCarrier().toString())
            .setStatus(TrackingStatus.IN_TRANSIT)
            .setLocation(null)
            .setDescription("Shipment picked up from FC")
            .setOccurredAt(Instant.now().toEpochMilli())
            .setEventTime(Instant.now().toEpochMilli())
            .build();
        publisher.publish("tracking-events", event.getTrackingNumber().toString(), tracking);

        meterRegistry.counter("carrier.manifest.transmitted").increment();
        log.info("Manifest transmitted orderId={}", event.getOrderId());
        return null;
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
