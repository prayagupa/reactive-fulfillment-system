package com.shipping.inventory.domain.model;

import com.shipping.cqrs.AggregateResult;
import com.shipping.inventory.domain.event.InventoryDomainEvent;
import com.shipping.inventory.domain.event.InventoryInsufficientEvent;
import com.shipping.inventory.domain.event.InventoryReservedEvent;

/**
 * Stock ledger aggregate root — one instance per {@code (fcId, sku)} pair.
 * <p>
 * Encapsulates the single critical inventory invariant:
 * <em>reserved stock must never exceed on-hand stock.</em>
 * <p>
 * The actual atomic persistence (Valkey Lua check-and-decrement as the fast
 * path, ScyllaDB LWT as the fallback) is an infrastructure concern owned by
 * the repository.  The aggregate records the <em>intent</em> and raises the
 * appropriate domain event; the repository enforces the intent atomically.
 *
 * <h3>Command handler contract</h3>
 * <pre>{@code
 * StockLedger ledger = inventoryRepository.findStockLedger(fcId, sku);
 * var result = ledger.reserve(quantity);
 * inventoryRepository.atomicReserve(result);   // Valkey Lua + ScyllaDB LWT
 * publisher.publish(result.events());
 * }</pre>
 */
public record StockLedger(String fcId, String sku, int onHand, int reserved) {

    // -------------------------------------------------------------------------
    // Domain method
    // -------------------------------------------------------------------------

    /**
     * Attempts to soft-reserve {@code quantity} units.
     * <p>
     * Invariant: {@code onHand - reserved >= quantity} must hold.
     *
     * @param quantity units to reserve
     * @return aggregate result carrying:
     *         <ul>
     *           <li>the updated ledger (reserved incremented) on success, or
     *               this unchanged ledger on shortage</li>
     *           <li>an {@link InventoryReservedEvent} on success, or an
     *               {@link InventoryInsufficientEvent} on shortage</li>
     *         </ul>
     */
    public AggregateResult<StockLedger, InventoryDomainEvent> reserve(int quantity) {
        int available = onHand - reserved;
        if (available < quantity) {
            return AggregateResult.of(this,
                    new InventoryInsufficientEvent(fcId, sku, quantity, available));
        }
        StockLedger next = new StockLedger(fcId, sku, onHand, reserved + quantity);
        return AggregateResult.of(next, new InventoryReservedEvent(fcId, sku, quantity));
    }
}
