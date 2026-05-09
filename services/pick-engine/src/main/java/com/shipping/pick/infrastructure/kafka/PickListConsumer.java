package com.shipping.pick.infrastructure.kafka;

import com.shipping.events.PickList;
import com.shipping.pick.application.command.CreatePickTasksCommand;
import com.shipping.pick.application.command.CreatePickTasksCommandHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes PickList events from wave-planner and dispatches a
 * {@link CreatePickTasksCommand} on the CQRS write side.
 */
@Component
public class PickListConsumer {

    private static final Logger log = LoggerFactory.getLogger(PickListConsumer.class);
    private final CreatePickTasksCommandHandler createPickTasksHandler;

    public PickListConsumer(CreatePickTasksCommandHandler createPickTasksHandler) {
        this.createPickTasksHandler = createPickTasksHandler;
    }

    @KafkaListener(topics = "picklists", groupId = "pick-engine",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPickList(ConsumerRecord<String, PickList> record, Acknowledgment ack) {
        PickList event = record.value();
        log.info("Received PickList pickListId={} orderId={}", event.getPickListId(), event.getOrderId());
        try {
            createPickTasksHandler.handle(new CreatePickTasksCommand(event));
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to materialise pick tasks pickListId={}: {}", event.getPickListId(), ex.getMessage());
        }
    }
}
