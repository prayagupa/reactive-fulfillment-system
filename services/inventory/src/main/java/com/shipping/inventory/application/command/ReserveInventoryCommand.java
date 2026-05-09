package com.shipping.inventory.application.command;

import com.shipping.events.OrderReceived;
import com.shipping.cqrs.Command;

/**
 * Write-side intent: attempt to soft-reserve inventory for all items in an order.
 *
 * @param order the OrderReceived Avro event consumed from Kafka
 */
public record ReserveInventoryCommand(OrderReceived order) implements Command {}
