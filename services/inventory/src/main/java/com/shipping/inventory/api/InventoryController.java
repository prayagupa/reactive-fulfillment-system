package com.shipping.inventory.api;

import com.shipping.inventory.application.query.GetStockQuery;
import com.shipping.inventory.application.query.GetStockQueryHandler;
import com.shipping.inventory.application.query.StockResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final GetStockQueryHandler getStockHandler;

    public InventoryController(GetStockQueryHandler getStockHandler) {
        this.getStockHandler = getStockHandler;
    }

    @GetMapping("/{fcId}/{sku}")
    public StockResult getStock(@PathVariable String fcId, @PathVariable String sku) {
        return getStockHandler.handle(new GetStockQuery(fcId, sku));
    }
}
