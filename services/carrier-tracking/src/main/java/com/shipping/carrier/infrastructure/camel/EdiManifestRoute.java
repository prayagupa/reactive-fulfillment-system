package com.shipping.carrier.infrastructure.camel;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for EDI X12 manifest transmission.
 *
 * Route: OrderPacked → build X12 856 ASN → transmit via SFTP to carrier EDI endpoint.
 * Carrier ACK (X12 997) is received on a separate inbound route and triggers
 * the OrderShipped event publication.
 *
 * In dev/staging the SFTP target is replaced by a local file drop for testing.
 */
@Component
public class EdiManifestRoute extends RouteBuilder {

    @Override
    public void configure() {

        // ── Outbound: transmit X12 856 ASN ────────────────────────────────────
        from("direct:transmit-manifest")
            .routeId("manifest-transmit")
            .log("Transmitting EDI X12 856 ASN for shipmentId=${header.shipmentId}")
            .marshal().edi("X12", "856")
            .choice()
                .when(simple("${properties:carrier.edi.mode} == 'sftp'"))
                    .toD("sftp:{{carrier.edi.sftp.host}}:{{carrier.edi.sftp.port}}"
                       + "/outbound?username={{carrier.edi.sftp.user}}"
                       + "&password={{carrier.edi.sftp.password}}"
                       + "&fileName=${header.shipmentId}.edi")
                .otherwise()
                    // Dev mode: write to local /tmp for inspection
                    .toD("file:/tmp/edi-outbound?fileName=${header.shipmentId}.edi")
            .end()
            .log("EDI transmission complete for shipmentId=${header.shipmentId}");

        // ── Inbound: receive X12 997 ACK from carrier ─────────────────────────
        from("file:{{carrier.edi.ack.drop:./edi-ack}}?noop=true&include=.*\\.997")
            .routeId("manifest-ack")
            .log("Received X12 997 ACK: ${header.CamelFileName}")
            .unmarshal().edi("X12", "997")
            .bean("ackProcessor", "processAck");
    }
}
