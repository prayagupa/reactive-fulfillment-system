package com.shipping.inventory.application.query;

import com.shipping.cqrs.QueryHandler;
import com.shipping.inventory.infrastructure.persistence.InventoryRepository;
import org.springframework.stereotype.Service;

/**
 * CQRS read side: handles {@link GetStockQuery}.
 * <p>
 * Reads the {@code inventory_ledger} table directly and returns the
 * currently available quantity (on_hand − reserved) for a single SKU at one FC.
 */
@Service
public class GetStockQueryHandler implements QueryHandler<GetStockQuery, StockResult> {

    private final InventoryRepository inventoryRepository;

    public GetStockQueryHandler(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public StockResult handle(GetStockQuery query) {
        int available = inventoryRepository.getAvailable(query.fcId(), query.sku());
        return new StockResult(query.fcId(), query.sku(), available);
    }
}
