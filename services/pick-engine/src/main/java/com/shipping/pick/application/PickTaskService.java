package com.shipping.pick.application;

/**
 * @deprecated Superseded by CQRS split:
 * <ul>
 *   <li>Write side: {@link com.shipping.pick.application.command.CreatePickTasksCommandHandler}</li>
 *   <li>Write side: {@link com.shipping.pick.application.command.ConfirmScanCommandHandler}</li>
 *   <li>Read  side: {@link com.shipping.pick.application.query.NextTaskQueryHandler}</li>
 *   <li>Read  side: {@link com.shipping.pick.application.query.PickListStatusQueryHandler}</li>
 * </ul>
 * This class is retained only to preserve git history; it is no longer wired into the application context.
 */
@Deprecated(forRemoval = true)
public final class PickTaskService {}

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
            PickTask task = new PickTask(
                pickList.getPickListId().toString(),
                pickList.getOrderId().toString(),
                item.getItemSeq(),
                item.getSku().toString(),
                0,   // quantityRequired resolved from wave data in full implementation
                item.getBinLocation().toString(),
                null,
                PickTask.Status.PENDING);
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
        if (!task.sku().equals(scannedBarcode)) {
            meterRegistry.counter("pick.scan.mismatch").increment();
            return Map.of("status", "MISMATCH", "expected", task.sku(), "scanned", scannedBarcode);
        }
        if (task.quantityRequired() != quantity) {
            return Map.of("status", "QTY_MISMATCH",
                "expected", task.quantityRequired(), "scanned", quantity);
        }

        task = task.withStatus(PickTask.Status.PICKED);
        repository.update(task);
        meterRegistry.counter("pick.items.confirmed").increment();
        return Map.of("status", "OK", "itemSeq", itemSeq);
    }

    public boolean isPickListComplete(String pickListId) {
        List<PickTask> tasks = repository.findByPickList(pickListId);
        boolean complete = tasks.stream().allMatch(t -> t.status() == PickTask.Status.PICKED);

        if (complete && !tasks.isEmpty()) {
            String orderId = tasks.get(0).orderId();
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
