package com.shipping.inventory.application.query;

import com.shipping.cqrs.Query;

/**
 * Read-side intent: get available stock for a single SKU at a given FC.
 *
 * @param fcId fulfilment centre identifier
 * @param sku  stock-keeping unit
 */
public record GetStockQuery(String fcId, String sku) implements Query<StockResult> {}
