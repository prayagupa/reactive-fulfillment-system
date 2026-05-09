package com.shipping.inventory.application.query;

/**
 * Read model: current available stock for one SKU at one FC.
 *
 * @param fcId      fulfilment centre
 * @param sku       stock-keeping unit
 * @param available on_hand minus reserved units
 */
public record StockResult(String fcId, String sku, int available) {}
