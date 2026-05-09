package com.shipping.order.api;

import com.shipping.order.api.dto.CreateOrderRequest;
import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order.
     * Idempotent: repeated calls with the same {@code Idempotency-Key} return the
     * original response without creating a duplicate order.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<CreateOrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        return orderService.createOrder(idempotencyKey, request);
    }

    @GetMapping("/{orderId}")
    public Mono<CreateOrderResponse> getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }
}
