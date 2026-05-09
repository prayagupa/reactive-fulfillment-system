package com.shipping.order.application.query;

import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.cqrs.QueryHandler;
import com.shipping.order.infrastructure.persistence.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * CQRS read side: handles {@link GetOrderQuery}.
 * <p>
 * Queries ScyllaDB for the order record and maps it to the read model.
 */
@Service
public class GetOrderQueryHandler
        implements QueryHandler<GetOrderQuery, Mono<CreateOrderResponse>> {

    private static final Logger log = LoggerFactory.getLogger(GetOrderQueryHandler.class);

    private final OrderRepository orderRepository;

    public GetOrderQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Mono<CreateOrderResponse> handle(GetOrderQuery query) {
        return orderRepository.findById(UUID.fromString(query.orderId()))
            .flatMap(opt -> opt
                .map(o -> Mono.just(new CreateOrderResponse(
                    o.orderId().toString(), o.status().name())))
                .orElseGet(() -> Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Order not found: " + query.orderId()))));
    }
}
