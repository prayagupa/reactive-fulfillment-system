package com.shipping.pick.application.command;

import com.shipping.cqrs.AggregateResult;
import com.shipping.cqrs.CommandHandler;
import com.shipping.events.PickCompleted;
import com.shipping.kafka.producer.DomainEventPublisher;
import com.shipping.pick.domain.event.PickDomainEvent;
import com.shipping.pick.domain.event.PickListCompletedEvent;
import com.shipping.pick.domain.event.PickShortEvent;
import com.shipping.pick.domain.event.ScanConfirmedEvent;
import com.shipping.pick.domain.model.PickList;
import com.shipping.pick.infrastructure.persistence.PickListRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * CQRS write side: handles {@link ConfirmScanCommand}.
 * <p>
 * Thin coordinator — delegates all business logic to
 * {@link PickList#confirmScan(int, String, int)} and
 * {@link PickList#markShort(int)}:
 * <ol>
 *   <li>Load the {@link PickList} aggregate via {@link PickListRepository}.</li>
 *   <li>Call the domain method (invariant checking inside the aggregate).</li>
 *   <li>Persist the updated task via the repository.</li>
 *   <li>Map and publish the resulting {@link PickDomainEvent} to Kafka.</li>
 * </ol>
 */
@Service
public class ConfirmScanCommandHandler
        implements CommandHandler<ConfirmScanCommand, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ConfirmScanCommandHandler.class);
    private static final String PICK_EVENTS_TOPIC = "pick-events";

    private final PickListRepository pickListRepository;
    private final DomainEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public ConfirmScanCommandHandler(PickListRepository pickListRepository,
                                     DomainEventPublisher publisher,
                                     MeterRegistry meterRegistry) {
        this.pickListRepository = pickListRepository;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Map<String, Object> handle(ConfirmScanCommand cmd) {
        // ── Step 1: load aggregate ────────────────────────────────────────────
        PickList pickList = pickListRepository.findByPickListId(cmd.pickListId());
        if (pickList == null) {
            return Map.of("status", "NOT_FOUND");
        }

        // ── Step 2: call domain method (invariants enforced inside PickList) ──
        AggregateResult<PickList, PickDomainEvent> result;
        try {
            result = pickList.confirmScan(cmd.itemSeq(), cmd.scannedBarcode(), cmd.quantity());
        } catch (IllegalArgumentException ex) {
            meterRegistry.counter("pick.scan.mismatch").increment();
            log.warn("Scan rejected pickListId={} itemSeq={}: {}",
                     cmd.pickListId(), cmd.itemSeq(), ex.getMessage());
            return Map.of("status", "REJECTED", "reason", ex.getMessage());
        }

        // ── Step 3: persist updated task ──────────────────────────────────────
        result.state().tasks().stream()
            .filter(t -> t.itemSeq() == cmd.itemSeq())
            .findFirst()
            .ifPresent(pickListRepository::updateTask);

        meterRegistry.counter("pick.items.confirmed").increment();

        // ── Step 4: publish domain event ──────────────────────────────────────
        boolean listComplete = publishDomainEvent(result);

        log.info("Scan confirmed pickListId={} itemSeq={} complete={}",
                 cmd.pickListId(), cmd.itemSeq(), listComplete);
        return Map.of("status", "OK", "itemSeq", cmd.itemSeq(), "pickListComplete", listComplete);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean publishDomainEvent(AggregateResult<PickList, PickDomainEvent> result) {
        for (PickDomainEvent event : result.events()) {
            switch (event) {
                case PickListCompletedEvent e -> {
                    PickCompleted avro = PickCompleted.newBuilder()
                        .setPickListId(e.pickListId())
                        .setOrderId(e.orderId())
                        .setFcId(e.fcId())
                        .setPickedBy("system")
                        .setCompletedAt(Instant.now())
                        .setEventTime(Instant.now())
                        .build();
                    publisher.publish(PICK_EVENTS_TOPIC, e.orderId(), avro);
                    meterRegistry.counter("pick.list.completed").increment();
                    log.info("PickCompleted published orderId={} pickListId={}",
                             e.orderId(), e.pickListId());
                    return true;
                }
                case ScanConfirmedEvent e ->
                    log.debug("ScanConfirmed pickListId={} itemSeq={} sku={}",
                              e.pickListId(), e.itemSeq(), e.sku());
                case PickShortEvent e ->
                    log.warn("PickShort published pickListId={} itemSeq={}",
                             e.pickListId(), e.itemSeq());
            }
        }
        return false;
    }
}
