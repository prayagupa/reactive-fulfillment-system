package com.shipping.inventory.application.command;

import com.shipping.events.InventoryInsufficient;
import com.shipping.events.InventoryReserved;
import com.shipping.events.OrderReceived;
import com.shipping.events.Reservation;
import com.shipping.events.Shortage;
import com.shipping.cqrs.CommandHandler;
import com.shipping.inventory.infrastructure.persistence.InventoryRepository;
import com.shipping.kafka.producer.DomainEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * CQRS write side: handles {@link ReserveInventoryCommand}.
 * <p>
 * Attempts a soft-reservation for every item in the order using:
 * <ol>
 *   <li>Valkey atomic Lua check-and-decrement (fast path)</li>
 *   <li>ScyllaDB LWT fallback on cache miss</li>
 * </ol>
 * Publishes {@code InventoryReserved} or {@code InventoryInsufficient} to Kafka.
 */
@Service
public class ReserveInventoryCommandHandler
        implements CommandHandler<ReserveInventoryCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(ReserveInventoryCommandHandler.class);
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    // Lua: atomic check-and-decrement on Valkey
    // KEYS[1] = "inv:{fcId}:{sku}"  ARGV[1] = quantity
    private static final String RESERVE_LUA = """
        local current = tonumber(redis.call('GET', KEYS[1]))
        if current == nil then return -1 end
        if current < tonumber(ARGV[1]) then return -2 end
        return redis.call('DECRBY', KEYS[1], ARGV[1])
        """;

    private final InventoryRepository inventoryRepository;
    private final DomainEventPublisher publisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    public ReserveInventoryCommandHandler(InventoryRepository inventoryRepository,
                                          DomainEventPublisher publisher,
                                          RedisTemplate<String, String> redisTemplate,
                                          MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.publisher = publisher;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Void handle(ReserveInventoryCommand cmd) {
        OrderReceived order = cmd.order();
        String fcId = inventoryRepository.findBestFc(order);
        List<Reservation> reservations = new ArrayList<>();
        List<Shortage> shortages = new ArrayList<>();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_LUA, Long.class);

        for (var item : order.getItems()) {
            String key = "inv:" + fcId + ":" + item.getSku();
            Long result = redisTemplate.execute(script, List.of(key), String.valueOf(item.getQuantity()));

            if (result == null || result < 0) {
                int available = inventoryRepository.getAvailable(fcId, item.getSku().toString());
                if (available < item.getQuantity()) {
                    shortages.add(Shortage.newBuilder()
                        .setSku(item.getSku().toString())
                        .setRequested(item.getQuantity())
                        .setAvailable(available)
                        .build());
                } else {
                    inventoryRepository.softReserve(fcId, item.getSku().toString(), item.getQuantity());
                    reservations.add(Reservation.newBuilder()
                        .setSku(item.getSku().toString())
                        .setQuantity(item.getQuantity())
                        .setBinLocation(null)
                        .build());
                }
            } else {
                reservations.add(Reservation.newBuilder()
                    .setSku(item.getSku().toString())
                    .setQuantity(item.getQuantity())
                    .setBinLocation(null)
                    .build());
            }
        }

        if (!shortages.isEmpty()) {
            InventoryInsufficient event = InventoryInsufficient.newBuilder()
                .setOrderId(order.getOrderId().toString())
                .setShortages(shortages)
                .setEventTime(Instant.now().toEpochMilli())
                .build();
            publisher.publish(INVENTORY_EVENTS_TOPIC, order.getOrderId().toString(), event);
            meterRegistry.counter("inventory.reservation.insufficient").increment();
            log.warn("Inventory insufficient orderId={}", order.getOrderId());
        } else {
            InventoryReserved event = InventoryReserved.newBuilder()
                .setOrderId(order.getOrderId().toString())
                .setFcId(fcId)
                .setReservations(reservations)
                .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).toEpochMilli())
                .setEventTime(Instant.now().toEpochMilli())
                .build();
            publisher.publish(INVENTORY_EVENTS_TOPIC, order.getOrderId().toString(), event);
            meterRegistry.counter("inventory.reservation.success").increment();
            log.info("Inventory reserved orderId={}", order.getOrderId());
        }
        return null;
    }
}
