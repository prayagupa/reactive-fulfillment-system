package com.shipping.order.infrastructure.kafka;

import com.shipping.events.Address;
import com.shipping.events.OrderItem;
import com.shipping.events.OrderReceived;
import com.shipping.kafka.producer.DomainEventPublisher;
import com.shipping.order.domain.event.OrderDomainEvent;
import com.shipping.order.domain.event.OrderReceivedEvent;
import com.shipping.order.domain.event.OrderRoutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter: maps {@link OrderDomainEvent} instances (raised by
 * the {@link com.shipping.order.domain.model.Order} aggregate) to their Avro
 * counterparts and publishes them to Kafka.
 * <p>
 * This class is the only place that knows about both the internal domain model
 * and the external Avro schema — it is the Anti-Corruption Layer between the
 * domain and the event backbone.
 */
@Component
public class OrderKafkaProducer {

    static final String ORDERS_TOPIC = "orders";
    private static final Logger log = LoggerFactory.getLogger(OrderKafkaProducer.class);

    private final DomainEventPublisher publisher;

    public OrderKafkaProducer(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Dispatches a domain event to its Avro representation and publishes it.
     * Uses a switch expression over the sealed {@link OrderDomainEvent} hierarchy
     * so the compiler enforces exhaustiveness.
     */
    public void publish(OrderDomainEvent domainEvent) {
        switch (domainEvent) {
            case OrderReceivedEvent e -> publishOrderReceived(e);
            case OrderRoutedEvent e   -> publishOrderRouted(e);
        }
    }

    // ── Private mappers ───────────────────────────────────────────────────────

    private void publishOrderReceived(OrderReceivedEvent e) {
        List<OrderItem> avroItems = e.items().stream()
            .map(i -> OrderItem.newBuilder()
                .setSku(i.sku())
                .setQuantity(i.quantity())
                .setUnitPrice(i.unitPrice())
                .build())
            .collect(Collectors.toList());

        com.shipping.order.domain.model.Address addr = e.shippingAddress();
        Address avroAddress = Address.newBuilder()
            .setLine1(addr.line1())
            .setLine2(addr.line2())
            .setCity(addr.city())
            .setState(addr.state())
            .setPostalCode(addr.postalCode())
            .setCountryCode(addr.countryCode())
            .build();

        OrderReceived avro = OrderReceived.newBuilder()
            .setOrderId(e.orderId().toString())
            .setIdempotencyKey(e.idempotencyKey())
            .setCustomerId(e.customerId())
            .setItems(avroItems)
            .setShippingAddress(avroAddress)
            .setRequestedDeliveryDate(e.requestedDeliveryDate())
            .setEventTime(Instant.now())
            .build();

        publisher.publish(ORDERS_TOPIC, e.orderId().toString(), avro);
        log.info("Published OrderReceived orderId={}", e.orderId());
    }

    private void publishOrderRouted(OrderRoutedEvent e) {
        // Avro OrderRouted schema lives in libs/common-events
        com.shipping.events.OrderRouted avro = com.shipping.events.OrderRouted.newBuilder()
            .setOrderId(e.orderId().toString())
            .setFcId(e.fcId())
            .setRoutingScore(0.0)    // populated by FC router in full implementation
            .setEventTime(Instant.now())
            .build();

        publisher.publish(ORDERS_TOPIC, e.orderId().toString(), avro);
        log.info("Published OrderRouted orderId={} fcId={}", e.orderId(), e.fcId());
    }
}

