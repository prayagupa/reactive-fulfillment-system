package com.shipping.carrier.application.command;

import com.shipping.events.OrderPacked;
import com.shipping.cqrs.Command;

/**
 * Write-side intent: generate and transmit an EDI manifest for a packed order.
 *
 * @param orderPacked the OrderPacked Avro event consumed from Kafka
 */
public record TransmitManifestCommand(OrderPacked orderPacked) implements Command {}
