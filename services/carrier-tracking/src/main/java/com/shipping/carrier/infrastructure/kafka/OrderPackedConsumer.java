package com.shipping.carrier.infrastructure.kafka;

import com.shipping.events.OrderPacked;
import com.shipping.carrier.application.ManifestService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes OrderPacked events and triggers carrier manifest generation.
 */
@Component
public class OrderPackedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPackedConsumer.class);
    private final ManifestService manifestService;

    public OrderPackedConsumer(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @KafkaListener(topics = "shipments", groupId = "carrier-tracking",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderPacked(ConsumerRecord<String, OrderPacked> record, Acknowledgment ack) {
        OrderPacked event = record.value();
        log.info("Processing OrderPacked orderId={} trackingNumber={}",
            event.getOrderId(), event.getTrackingNumber());
        try {
            manifestService.processOrderPacked(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to generate manifest for orderId={}: {}", event.getOrderId(), ex.getMessage());
        }
    }
}
