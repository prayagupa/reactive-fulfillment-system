package com.shipping.order.application.query;

import com.shipping.cqrs.Query;
import com.shipping.order.api.dto.CreateOrderResponse;
import reactor.core.publisher.Mono;

/**
 * Read-side intent: fetch an order by its UUID string.
 *
 * @param orderId UUID string of the order to look up
 */
public record GetOrderQuery(String orderId) implements Query<Mono<CreateOrderResponse>> {}
