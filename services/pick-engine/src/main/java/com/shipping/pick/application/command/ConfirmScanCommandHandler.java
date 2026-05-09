package com.shipping.pick.application.command;

import com.shipping.events.PickCompleted;
import com.shipping.kafka.producer.DomainEventPublisher;
import com.shipping.cqrs.CommandHandler;
import com.shipping.pick.domain.model.PickTask;
import com.shipping.pick.infrastructure.persistence.PickTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CQRS write side: handles {@link ConfirmScanCommand}.
 * <p>
 * Validates the scanned barcode against the expected SKU and quantity,
 * transitions the task to PICKED, and — if the whole pick list is now
 * complete — publishes a {@code PickCompleted} event.
 */
@Service
public class ConfirmScanCommandHandler
        implements CommandHandler<ConfirmScanCommand, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ConfirmScanCommandHandler.class);
    private static final String PICK_EVENTS_TOPIC = "pick-events";

    private final PickTaskRepository repository;
    private final DomainEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public ConfirmScanCommandHandler(PickTaskRepository repository,
                                     DomainEventPublisher publisher,
                                     MeterRegistry meterRegistry) {
        this.repository = repository;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Map<String, Object> handle(ConfirmScanCommand cmd) {
        PickTask task = repository.findByPickListAndSeq(cmd.pickListId(), cmd.itemSeq());

        if (task == null) {
            return Map.of("status", "NOT_FOUND");
        }
        if (!task.sku().equals(cmd.scannedBarcode())) {
            meterRegistry.counter("pick.scan.mismatch").increment();
            return Map.of("status", "MISMATCH", "expected", task.sku(), "scanned", cmd.scannedBarcode());
        }
        if (task.quantityRequired() != cmd.quantity()) {
            return Map.of("status", "QTY_MISMATCH",
                "expected", task.quantityRequired(), "scanned", cmd.quantity());
        }

        task = task.withStatus(PickTask.Status.PICKED);
        repository.update(task);
        meterRegistry.counter("pick.items.confirmed").increment();

        // Check if whole pick list is now complete
        List<PickTask> tasks = repository.findByPickList(cmd.pickListId());
        boolean complete = tasks.stream().allMatch(t -> t.status() == PickTask.Status.PICKED);
        if (complete && !tasks.isEmpty()) {
            String orderId = tasks.get(0).orderId();
            PickCompleted event = PickCompleted.newBuilder()
                .setPickListId(cmd.pickListId())
                .setOrderId(orderId)
                .setFcId("FC-EAST-1")
                .setPickedBy("system")
                .setCompletedAt(Instant.now().toEpochMilli())
                .setEventTime(Instant.now().toEpochMilli())
                .build();
            publisher.publish(PICK_EVENTS_TOPIC, orderId, event);
            log.info("PickCompleted published orderId={} pickListId={}", orderId, cmd.pickListId());
            meterRegistry.counter("pick.list.completed").increment();
        }

        return Map.of("status", "OK", "itemSeq", cmd.itemSeq(), "pickListComplete", complete);
    }
}
