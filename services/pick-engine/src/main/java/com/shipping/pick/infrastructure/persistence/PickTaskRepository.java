package com.shipping.pick.infrastructure.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.shipping.pick.domain.model.PickTask;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * ScyllaDB repository for pick tasks.
 *
 * Table DDL:
 * <pre>
 *   CREATE TABLE pick_tasks (
 *     pick_list_id    TEXT,
 *     item_seq        INT,
 *     order_id        TEXT,
 *     sku             TEXT,
 *     bin_location    TEXT,
 *     quantity        INT,
 *     status          TEXT,
 *     picked_by       TEXT,
 *     PRIMARY KEY (pick_list_id, item_seq)
 *   );
 * </pre>
 */
@Repository
public class PickTaskRepository {

    private final CqlSession session;
    private PreparedStatement insertStmt;
    private PreparedStatement findByListStmt;
    private PreparedStatement findByListAndSeqStmt;
    private PreparedStatement updateStatusStmt;

    public PickTaskRepository(CqlSession session) {
        this.session = session;
    }

    @PostConstruct
    void prepare() {
        insertStmt = session.prepare(
            "INSERT INTO pick_tasks (pick_list_id, item_seq, order_id, sku, bin_location, quantity, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)");
        findByListStmt = session.prepare(
            "SELECT * FROM pick_tasks WHERE pick_list_id = ?");
        findByListAndSeqStmt = session.prepare(
            "SELECT * FROM pick_tasks WHERE pick_list_id = ? AND item_seq = ?");
        updateStatusStmt = session.prepare(
            "UPDATE pick_tasks SET status = ?, picked_by = ? WHERE pick_list_id = ? AND item_seq = ?");
    }

    public void save(PickTask task) {
        session.execute(insertStmt.bind(
            task.pickListId(), task.itemSeq(), task.orderId(),
            task.sku(), task.binLocation(), task.quantityRequired(),
            task.status().name()));
    }

    public void update(PickTask task) {
        session.execute(updateStatusStmt.bind(
            task.status().name(), task.pickedBy(),
            task.pickListId(), task.itemSeq()));
    }

    public List<PickTask> findByPickList(String pickListId) {
        var rows = session.execute(findByListStmt.bind(pickListId));
        List<PickTask> tasks = new ArrayList<>();
        for (var row : rows) {
            tasks.add(new PickTask(
                row.getString("pick_list_id"),
                row.getString("order_id"),
                row.getInt("item_seq"),
                row.getString("sku"),
                row.getInt("quantity"),
                row.getString("bin_location"),
                row.getString("picked_by"),
                PickTask.Status.valueOf(row.getString("status"))));
        }
        return tasks;
    }

    public PickTask findByPickListAndSeq(String pickListId, int itemSeq) {
        var row = session.execute(findByListAndSeqStmt.bind(pickListId, itemSeq)).one();
        if (row == null) return null;
        return new PickTask(
            row.getString("pick_list_id"),
            row.getString("order_id"),
            row.getInt("item_seq"),
            row.getString("sku"),
            row.getInt("quantity"),
            row.getString("bin_location"),
            row.getString("picked_by"),
            PickTask.Status.valueOf(row.getString("status")));
    }

    /** Stub: returns the next PENDING task. Real impl adds associate routing. */
    public PickTask findNextPending(String associateId) {
        return null; // TODO: query by status = PENDING LIMIT 1 with token-range pagination
    }
}
