package com.shipping.inventory.infrastructure.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.shipping.events.OrderReceived;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

/**
 * ScyllaDB repository for the inventory ledger.
 *
 * Table DDL:
 * <pre>
 *   CREATE TABLE inventory_ledger (
 *     fc_id     TEXT,
 *     sku       TEXT,
 *     on_hand   INT,
 *     reserved  INT,
 *     allocated INT,
 *     PRIMARY KEY (fc_id, sku)
 *   );
 * </pre>
 */
@Repository
public class InventoryRepository {

    private final CqlSession session;
    private PreparedStatement getAvailableStmt;
    private PreparedStatement softReserveStmt;

    public InventoryRepository(CqlSession session) {
        this.session = session;
    }

    @PostConstruct
    void prepare() {
        getAvailableStmt = session.prepare(
            "SELECT on_hand, reserved FROM inventory_ledger WHERE fc_id = ? AND sku = ?");
        softReserveStmt = session.prepare(
            "UPDATE inventory_ledger SET reserved = reserved + ? " +
            "WHERE fc_id = ? AND sku = ? IF (on_hand - reserved) >= ?");
    }

    public int getAvailable(String fcId, String sku) {
        var row = session.execute(getAvailableStmt.bind(fcId, sku)).one();
        if (row == null) return 0;
        return row.getInt("on_hand") - row.getInt("reserved");
    }

    public boolean softReserve(String fcId, String sku, int qty) {
        return session.execute(softReserveStmt.bind(qty, fcId, sku, qty)).wasApplied();
    }

    /**
     * Stub: returns the first FC that appears to have stock.
     * Real implementation ranks FCs by coverage × distance × SLA.
     */
    public String findBestFc(OrderReceived order) {
        return "FC-EAST-1";
    }
}
