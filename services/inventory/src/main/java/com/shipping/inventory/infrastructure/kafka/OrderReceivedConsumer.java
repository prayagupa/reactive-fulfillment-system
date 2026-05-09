package com.shipping.inventory.infrastructure.kafka;

import com.shipping.events.InventoryInsufficient;
import com.shipping.events.InventoryReserved;
import com.shipping.events.OrderReceived;
import com.shipping.inventory.application.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code OrderReceived} events and attempts to soft-reserve inventory.
 * Manual ack ensures at-least-once processing; InventoryService is idempotent
 * via ScyllaDB LWT.
 */
@Component
public class OrderReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderReceivedConsumer.class);

    private final InventoryService inventoryService;

    public OrderReceivedConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderReceived(ConsumerRecord<String, OrderReceived> record, Acknowledgment ack) {
        OrderReceived event = record.value();
        log.info("Processing OrderReceived orderId={}", event.getOrderId());

        try {
            Object result = inventoryService.reserve(event);

            if (result instanceof InventoryReserved) {
                log.info("Inventory reserved orderId={}", event.getOrderId());
            } else if (result instanceof InventoryInsufficient) {
                log.warn("Inventory insufficient orderId={}", event.getOrderId());
            }

            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process OrderReceived orderId={}: {}", event.getOrderId(), ex.getMessage());
            // Do NOT ack — Kafka will redeliver after consumer restart
        }
    }
}
