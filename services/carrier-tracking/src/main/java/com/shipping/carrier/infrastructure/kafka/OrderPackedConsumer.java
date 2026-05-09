package com.shipping.carrier.infrastructure.kafka;

import com.shipping.events.OrderPacked;
import com.shipping.carrier.application.command.TransmitManifestCommand;
import com.shipping.carrier.application.command.TransmitManifestCommandHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes OrderPacked events and dispatches a
 * {@link TransmitManifestCommand} on the CQRS write side.
 */
@Component
public class OrderPackedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPackedConsumer.class);
    private final TransmitManifestCommandHandler transmitManifestHandler;

    public OrderPackedConsumer(TransmitManifestCommandHandler transmitManifestHandler) {
        this.transmitManifestHandler = transmitManifestHandler;
    }

    @KafkaListener(topics = "shipments", groupId = "carrier-tracking",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderPacked(ConsumerRecord<String, OrderPacked> record, Acknowledgment ack) {
        OrderPacked event = record.value();
        log.info("Processing OrderPacked orderId={} trackingNumber={}",
            event.getOrderId(), event.getTrackingNumber());
        try {
            transmitManifestHandler.handle(new TransmitManifestCommand(event));
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to generate manifest for orderId={}: {}", event.getOrderId(), ex.getMessage());
        }
    }
}
