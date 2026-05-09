package com.shipping.order.application;

import com.shipping.order.api.dto.CreateOrderRequest;
import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.order.domain.model.Address;
import com.shipping.order.domain.model.Order;
import com.shipping.order.domain.model.OrderItem;
import com.shipping.order.infrastructure.kafka.OrderKafkaProducer;
import com.shipping.order.infrastructure.persistence.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderKafkaProducer kafkaProducer;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository,
                        OrderKafkaProducer kafkaProducer,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.kafkaProducer = kafkaProducer;
        this.meterRegistry = meterRegistry;
    }

    public Mono<CreateOrderResponse> createOrder(String idempotencyKey,
                                                  CreateOrderRequest req) {
        // 1. Idempotency check via ScyllaDB
        return orderRepository.findByIdempotencyKey(idempotencyKey)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Duplicate order request, idempotencyKey={}", idempotencyKey);
                    meterRegistry.counter("order.duplicate").increment();
                    // Return existing order (simplified: return 202 with idempotency signal)
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Order already received for idempotency key: " + idempotencyKey));
                }

                // 2. Build domain object
                List<OrderItem> items = req.items().stream()
                    .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                    .collect(Collectors.toList());

                CreateOrderRequest.AddressDto a = req.shippingAddress();
                Address address = new Address(
                    a.line1(), a.line2(), a.city(),
                    a.state(), a.postalCode(), a.countryCode());

                Order order = Order.newOrder(idempotencyKey, req.customerId(),
                    items, address, req.requestedDeliveryDate());

                // 3. Persist to ScyllaDB (IF NOT EXISTS — second safety net)
                return orderRepository.save(order)
                    .flatMap(saved -> {
                        if (!saved) {
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Concurrent duplicate detected"));
                        }
                        // 4. Publish Kafka event
                        kafkaProducer.publishOrderReceived(order);
                        meterRegistry.counter("order.created").increment();
                        log.info("Order created orderId={}", order.orderId());
                        return Mono.just(new CreateOrderResponse(
                            order.orderId().toString(), order.status().name()));
                    });
            });
    }

    public Mono<CreateOrderResponse> getOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
            .flatMap(opt -> opt
                .map(o -> Mono.just(new CreateOrderResponse(
                    o.orderId().toString(), o.status().name())))
                .orElseGet(() -> Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Order not found: " + orderId))));
    }
}
