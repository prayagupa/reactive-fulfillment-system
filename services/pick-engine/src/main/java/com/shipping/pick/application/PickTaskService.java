package com.shipping.pick.application;

import com.shipping.events.PickCompleted;
import com.shipping.events.PickList;
import com.shipping.kafka.producer.DomainEventPublisher;
import com.shipping.pick.domain.model.PickTask;
import com.shipping.pick.infrastructure.persistence.PickTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PickTaskService {

    private static final Logger log = LoggerFactory.getLogger(PickTaskService.class);
    private static final String PICK_EVENTS_TOPIC = "pick-events";

    private final PickTaskRepository repository;
    private final DomainEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public PickTaskService(PickTaskRepository repository,
                           DomainEventPublisher publisher,
                           MeterRegistry meterRegistry) {
        this.repository = repository;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    public void createTasks(PickList pickList) {
        for (var item : pickList.getItems()) {
            PickTask task = new PickTask();
            task.setPickListId(pickList.getPickListId().toString());
            task.setOrderId(pickList.getOrderId().toString());
            task.setItemSeq(item.getItemSeq());
            task.setSku(item.getSku().toString());
            task.setBinLocation(item.getBinLocation().toString());
            task.setStatus(PickTask.Status.PENDING);
            repository.save(task);
        }
        log.info("Created {} pick tasks for pickListId={}", pickList.getItems().size(), pickList.getPickListId());
        meterRegistry.counter("pick.tasks.created").increment(pickList.getItems().size());
    }

    public PickTask nextTaskForAssociate(String associateId) {
        return repository.findNextPending(associateId);
    }

    /**
     * Validate a barcode scan and mark the item confirmed.
     */
    public Map<String, Object> confirmScan(String pickListId, int itemSeq,
                                           String scannedBarcode, int quantity) {
        PickTask task = repository.findByPickListAndSeq(pickListId, itemSeq);

        if (task == null) {
            return Map.of("status", "NOT_FOUND");
        }
        if (!task.getSku().equals(scannedBarcode)) {
            meterRegistry.counter("pick.scan.mismatch").increment();
            return Map.of("status", "MISMATCH", "expected", task.getSku(), "scanned", scannedBarcode);
        }
        if (task.getQuantityRequired() != quantity) {
            return Map.of("status", "QTY_MISMATCH",
                "expected", task.getQuantityRequired(), "scanned", quantity);
        }

        task.setStatus(PickTask.Status.PICKED);
        repository.update(task);
        meterRegistry.counter("pick.items.confirmed").increment();
        return Map.of("status", "OK", "itemSeq", itemSeq);
    }

    public boolean isPickListComplete(String pickListId) {
        List<PickTask> tasks = repository.findByPickList(pickListId);
        boolean complete = tasks.stream().allMatch(t -> t.getStatus() == PickTask.Status.PICKED);

        if (complete && !tasks.isEmpty()) {
            String orderId = tasks.get(0).getOrderId();
            PickCompleted event = PickCompleted.newBuilder()
                .setPickListId(pickListId)
                .setOrderId(orderId)
                .setFcId("FC-EAST-1")   // resolved from task metadata in full implementation
                .setPickedBy("system")
                .setCompletedAt(Instant.now().toEpochMilli())
                .setEventTime(Instant.now().toEpochMilli())
                .build();
            publisher.publish(PICK_EVENTS_TOPIC, orderId, event);
            log.info("PickCompleted published orderId={} pickListId={}", orderId, pickListId);
            meterRegistry.counter("pick.list.completed").increment();
        }

        return complete;
    }
}
