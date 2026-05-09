package com.shipping.order.application.command;

import com.shipping.order.api.dto.CreateOrderRequest;
import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.cqrs.AggregateResult;
import com.shipping.cqrs.CommandHandler;
import com.shipping.order.domain.event.OrderDomainEvent;
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
import java.util.stream.Collectors;

/**
 * CQRS write side: handles {@link CreateOrderCommand}.
 * <p>
 * Thin coordinator — enforces the four-step aggregate pattern:
 * <ol>
 *   <li>Idempotency guard (ScyllaDB LWT via repository).</li>
 *   <li>Build the {@link Order} aggregate via its factory and call
 *       {@link Order#receive()} to obtain the domain event.</li>
 *   <li>Persist {@code result.state()} to ScyllaDB (IF NOT EXISTS).</li>
 *   <li>Publish each {@link OrderDomainEvent} in {@code result.events()}
 *       to Kafka via {@link OrderKafkaProducer}.</li>
 * </ol>
 * Business rules (valid status transitions, invariant checks) live inside the
 * {@link Order} aggregate, not here.
 */
@Service
public class CreateOrderCommandHandler
        implements CommandHandler<CreateOrderCommand, Mono<CreateOrderResponse>> {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderCommandHandler.class);

    private final OrderRepository orderRepository;
    private final OrderKafkaProducer kafkaProducer;
    private final MeterRegistry meterRegistry;

    public CreateOrderCommandHandler(OrderRepository orderRepository,
                                     OrderKafkaProducer kafkaProducer,
                                     MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.kafkaProducer = kafkaProducer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<CreateOrderResponse> handle(CreateOrderCommand cmd) {
        String idempotencyKey = cmd.idempotencyKey();
        CreateOrderRequest req = cmd.request();

        // ── Step 1: idempotency guard ─────────────────────────────────────────
        return orderRepository.findByIdempotencyKey(idempotencyKey)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Duplicate order request idempotencyKey={}", idempotencyKey);
                    meterRegistry.counter("order.duplicate").increment();
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Order already received for idempotency key: " + idempotencyKey));
                }

                // ── Step 2: build aggregate + call domain method ──────────────
                List<OrderItem> items = req.items().stream()
                    .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                    .collect(Collectors.toList());

                CreateOrderRequest.AddressDto a = req.shippingAddress();
                Address address = new Address(
                    a.line1(), a.line2(), a.city(),
                    a.state(), a.postalCode(), a.countryCode());

                Order order = Order.newOrder(idempotencyKey, req.customerId(),
                    items, address, req.requestedDeliveryDate());

                AggregateResult<Order, OrderDomainEvent> result = order.receive();

                // ── Step 3: persist new state ─────────────────────────────────
                return orderRepository.save(result.state())
                    .flatMap(saved -> {
                        if (!saved) {
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Concurrent duplicate detected"));
                        }

                        // ── Step 4: publish domain events ─────────────────────
                        result.events().forEach(kafkaProducer::publish);

                        meterRegistry.counter("order.created").increment();
                        log.info("Order created orderId={}", result.state().orderId());
                        return Mono.just(new CreateOrderResponse(
                            result.state().orderId().toString(),
                            result.state().status().name()));
                    });
            });
    }
}

