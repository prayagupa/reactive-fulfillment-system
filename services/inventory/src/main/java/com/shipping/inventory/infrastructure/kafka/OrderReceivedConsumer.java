package com.shipping.inventory.infrastructure.kafka;

import com.shipping.events.OrderReceived;
import com.shipping.inventory.application.command.ReserveInventoryCommand;
import com.shipping.inventory.application.command.ReserveInventoryCommandHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code OrderReceived} events and dispatches a
 * {@link ReserveInventoryCommand} on the CQRS write side.
 */
@Component
public class OrderReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderReceivedConsumer.class);

    private final ReserveInventoryCommandHandler reserveHandler;

    public OrderReceivedConsumer(ReserveInventoryCommandHandler reserveHandler) {
        this.reserveHandler = reserveHandler;
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderReceived(ConsumerRecord<String, OrderReceived> record, Acknowledgment ack) {
        OrderReceived event = record.value();
        log.info("Processing OrderReceived orderId={}", event.getOrderId());
        try {
            reserveHandler.handle(new ReserveInventoryCommand(event));
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process OrderReceived orderId={}: {}", event.getOrderId(), ex.getMessage());
            // Do NOT ack — Kafka will redeliver after consumer restart
        }
    }
}
}
