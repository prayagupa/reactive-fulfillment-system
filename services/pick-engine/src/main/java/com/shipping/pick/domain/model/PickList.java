package com.shipping.pick.domain.model;

import com.shipping.cqrs.AggregateResult;
import com.shipping.pick.domain.event.PickDomainEvent;
import com.shipping.pick.domain.event.PickListCompletedEvent;
import com.shipping.pick.domain.event.PickShortEvent;
import com.shipping.pick.domain.event.ScanConfirmedEvent;

import java.util.List;

/**
 * Pick list aggregate root.
 * <p>
 * Owns an ordered collection of {@link PickTask} entities.  All state
 * transitions are domain methods that return an {@link AggregateResult}
 * carrying the updated aggregate state and the corresponding domain event.
 *
 * <h3>Invariants enforced here (not in the command handler)</h3>
 * <ul>
 *   <li>A task can only be confirmed if the scanned barcode matches the
 *       expected SKU.</li>
 *   <li>A task that is already {@code PICKED} cannot be confirmed again.</li>
 *   <li>{@link PickListCompletedEvent} is only raised when <em>all</em>
 *       tasks reach {@code PICKED} status.</li>
 * </ul>
 */
public record PickList(
        String pickListId,
        String orderId,
        String fcId,
        List<PickTask> tasks) {

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Reconstitutes a {@link PickList} from a flat list of {@link PickTask}
     * rows loaded by the repository.  Assumes all tasks share the same
     * {@code pickListId}, {@code orderId}, and FC (first task used as source).
     */
    public static PickList reconstitute(String pickListId, String orderId,
                                        String fcId, List<PickTask> tasks) {
        return new PickList(pickListId, orderId, fcId, List.copyOf(tasks));
    }

    /**
     * Creates a brand-new {@link PickList} from an inbound Avro
     * {@link com.shipping.events.PickList} event.
     */
    public static PickList from(com.shipping.events.PickList avro) {
        List<PickTask> tasks = avro.getItems().stream()
            .map(item -> new PickTask(
                avro.getPickListId().toString(),
                avro.getOrderId().toString(),
                avro.getFcId().toString(),
                item.getItemSeq(),
                item.getSku().toString(),
                item.getQuantity(),
                item.getBinLocation().toString(),
                null,
                PickTask.Status.PENDING))
            .toList();
        return new PickList(
            avro.getPickListId().toString(),
            avro.getOrderId().toString(),
            avro.getFcId().toString(),
            tasks);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /**
     * Records that a warehouse associate has scanned a barcode for one item.
     * <p>
     * Invariants:
     * <ul>
     *   <li>The item must exist in this pick list.</li>
     *   <li>The scanned barcode must match the expected SKU.</li>
     *   <li>The item must not already be PICKED.</li>
     * </ul>
     * Raises {@link PickListCompletedEvent} if this was the last pending task,
     * otherwise raises {@link ScanConfirmedEvent}.
     *
     * @param itemSeq       the sequence number of the picked item
     * @param scannedBarcode the barcode value read by the scanner
     * @param quantity       quantity scanned by the associate
     * @return aggregate result with updated pick list + appropriate domain event
     */
    public AggregateResult<PickList, PickDomainEvent> confirmScan(
            int itemSeq, String scannedBarcode, int quantity) {

        PickTask task = findTask(itemSeq);

        if (task.status() == PickTask.Status.PICKED) {
            throw new IllegalStateException(
                "Task itemSeq=%d in pickList=%s is already PICKED".formatted(itemSeq, pickListId));
        }
        if (!task.sku().equals(scannedBarcode)) {
            throw new IllegalArgumentException(
                "SKU mismatch for itemSeq=%d: expected '%s', scanned '%s'"
                    .formatted(itemSeq, task.sku(), scannedBarcode));
        }

        PickTask confirmed = task.withStatus(PickTask.Status.PICKED);
        List<PickTask> updated = replacedTasks(itemSeq, confirmed);
        PickList next = new PickList(pickListId, orderId, fcId, updated);

        boolean allPicked = updated.stream()
            .allMatch(t -> t.status() == PickTask.Status.PICKED
                       || t.status() == PickTask.Status.SHORT);

        PickDomainEvent event = allPicked
            ? new PickListCompletedEvent(pickListId, orderId, fcId)
            : new ScanConfirmedEvent(pickListId, orderId, itemSeq, task.sku());

        return AggregateResult.of(next, event);
    }

    /**
     * Records that an item could not be found in the expected bin location.
     * <p>
     * Invariant: the item must be in {@code PENDING} status.
     *
     * @param itemSeq the sequence number of the shorted item
     * @return aggregate result with the task marked SHORT + a {@link PickShortEvent}
     */
    public AggregateResult<PickList, PickDomainEvent> markShort(int itemSeq) {
        PickTask task = findTask(itemSeq);
        if (task.status() != PickTask.Status.PENDING) {
            throw new IllegalStateException(
                "Cannot short task itemSeq=%d in status %s".formatted(itemSeq, task.status()));
        }
        PickTask shorted = task.withStatus(PickTask.Status.SHORT);
        List<PickTask> updated = replacedTasks(itemSeq, shorted);
        PickList next = new PickList(pickListId, orderId, fcId, updated);
        return AggregateResult.of(next, new PickShortEvent(pickListId, orderId, itemSeq, task.sku()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PickTask findTask(int itemSeq) {
        return tasks.stream()
            .filter(t -> t.itemSeq() == itemSeq)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No task with itemSeq=%d in pickList=%s".formatted(itemSeq, pickListId)));
    }

    private List<PickTask> replacedTasks(int itemSeq, PickTask replacement) {
        return tasks.stream()
            .map(t -> t.itemSeq() == itemSeq ? replacement : t)
            .toList();
    }
}
