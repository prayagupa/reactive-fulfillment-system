package com.shipping.order.application.command;

import com.shipping.order.api.dto.CreateOrderRequest;
import com.shipping.order.api.dto.CreateOrderResponse;
import com.shipping.cqrs.CommandHandler;
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
 * Responsibilities:
 * <ol>
 *   <li>Idempotency guard via ScyllaDB LWT</li>
 *   <li>Build the {@link Order} domain record</li>
 *   <li>Persist to ScyllaDB (IF NOT EXISTS)</li>
 *   <li>Publish {@code OrderReceived} event to Kafka</li>
 * </ol>
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

        return orderRepository.findByIdempotencyKey(idempotencyKey)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Duplicate order request, idempotencyKey={}", idempotencyKey);
                    meterRegistry.counter("order.duplicate").increment();
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Order already received for idempotency key: " + idempotencyKey));
                }

                List<OrderItem> items = req.items().stream()
                    .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                    .collect(Collectors.toList());

                CreateOrderRequest.AddressDto a = req.shippingAddress();
                Address address = new Address(
                    a.line1(), a.line2(), a.city(),
                    a.state(), a.postalCode(), a.countryCode());

                Order order = Order.newOrder(idempotencyKey, req.customerId(),
                    items, address, req.requestedDeliveryDate());

                return orderRepository.save(order)
                    .flatMap(saved -> {
                        if (!saved) {
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Concurrent duplicate detected"));
                        }
                        kafkaProducer.publishOrderReceived(order);
                        meterRegistry.counter("order.created").increment();
                        log.info("Order created orderId={}", order.orderId());
                        return Mono.just(new CreateOrderResponse(
                            order.orderId().toString(), order.status().name()));
                    });
            });
    }
}
