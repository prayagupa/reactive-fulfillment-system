package com.shipping.order.infrastructure.kafka;

import com.shipping.events.Address;
import com.shipping.events.OrderItem;
import com.shipping.events.OrderReceived;
import com.shipping.kafka.producer.DomainEventPublisher;
import com.shipping.order.domain.model.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderKafkaProducer {

    static final String TOPIC = "orders";
    private final DomainEventPublisher publisher;

    public OrderKafkaProducer(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishOrderReceived(Order order) {
        List<OrderItem> avroItems = order.getItems().stream()
            .map(i -> OrderItem.newBuilder()
                .setSku(i.getSku())
                .setQuantity(i.getQuantity())
                .setUnitPrice(i.getUnitPrice())
                .build())
            .collect(Collectors.toList());

        com.shipping.order.domain.model.Address addr = order.getShippingAddress();
        Address avroAddress = Address.newBuilder()
            .setLine1(addr.getLine1())
            .setLine2(addr.getLine2())
            .setCity(addr.getCity())
            .setState(addr.getState())
            .setPostalCode(addr.getPostalCode())
            .setCountryCode(addr.getCountryCode())
            .build();

        OrderReceived event = OrderReceived.newBuilder()
            .setOrderId(order.getOrderId().toString())
            .setIdempotencyKey(order.getIdempotencyKey())
            .setCustomerId(order.getCustomerId())
            .setItems(avroItems)
            .setShippingAddress(avroAddress)
            .setRequestedDeliveryDate(order.getRequestedDeliveryDate())
            .setEventTime(Instant.now().toEpochMilli())
            .build();

        publisher.publish(TOPIC, order.getOrderId().toString(), event);
    }
}
