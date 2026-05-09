package com.shipping.inventory.api;

import com.shipping.inventory.infrastructure.persistence.InventoryRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryRepository repo;

    public InventoryController(InventoryRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{fcId}/{sku}")
    public Map<String, Object> getStock(@PathVariable String fcId, @PathVariable String sku) {
        int available = repo.getAvailable(fcId, sku);
        return Map.of("fcId", fcId, "sku", sku, "available", available);
    }
}
