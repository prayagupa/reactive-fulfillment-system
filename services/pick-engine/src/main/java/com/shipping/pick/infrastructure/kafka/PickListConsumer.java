package com.shipping.pick.infrastructure.kafka;

import com.shipping.events.PickList;
import com.shipping.pick.application.PickTaskService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes PickList events from wave-planner and materialises pick tasks in ScyllaDB.
 */
@Component
public class PickListConsumer {

    private static final Logger log = LoggerFactory.getLogger(PickListConsumer.class);
    private final PickTaskService pickTaskService;

    public PickListConsumer(PickTaskService pickTaskService) {
        this.pickTaskService = pickTaskService;
    }

    @KafkaListener(topics = "picklists", groupId = "pick-engine",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPickList(ConsumerRecord<String, PickList> record, Acknowledgment ack) {
        PickList event = record.value();
        log.info("Received PickList pickListId={} orderId={}", event.getPickListId(), event.getOrderId());
        try {
            pickTaskService.createTasks(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to materialise pick tasks pickListId={}: {}", event.getPickListId(), ex.getMessage());
        }
    }
}
