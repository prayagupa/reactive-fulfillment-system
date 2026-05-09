package com.shipping.order.api;

import com.shipping.order.api.dto.CreateOrderRequest;
import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.order.application.command.CreateOrderCommand;
import com.shipping.order.application.command.CreateOrderCommandHandler;
import com.shipping.order.application.query.GetOrderQuery;
import com.shipping.order.application.query.GetOrderQueryHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderCommandHandler createOrderHandler;
    private final GetOrderQueryHandler getOrderHandler;

    public OrderController(CreateOrderCommandHandler createOrderHandler,
                           GetOrderQueryHandler getOrderHandler) {
        this.createOrderHandler = createOrderHandler;
        this.getOrderHandler = getOrderHandler;
    }

    /**
     * Create a new order.
     * Idempotent: repeated calls with the same {@code Idempotency-Key} return
     * CONFLICT rather than creating a duplicate.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<CreateOrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        return createOrderHandler.handle(new CreateOrderCommand(idempotencyKey, request));
    }

    @GetMapping("/{orderId}")
    public Mono<CreateOrderResponse> getOrder(@PathVariable String orderId) {
        return getOrderHandler.handle(new GetOrderQuery(orderId));
    }
}
