package com.shipping.order.application.command;

import com.shipping.cqrs.Command;
import com.shipping.order.api.dto.CreateOrderRequest;

/**
 * Write-side intent: create a new order.
 *
 * @param idempotencyKey caller-supplied idempotency token
 * @param request        validated inbound request body
 */
public record CreateOrderCommand(String idempotencyKey, CreateOrderRequest request) implements Command {}
