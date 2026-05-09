package com.shipping.inventory.application;

import com.shipping.events.InventoryInsufficient;
import com.shipping.events.InventoryReserved;
import com.shipping.events.OrderReceived;
import com.shipping.events.Reservation;
import com.shipping.events.Shortage;
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

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    // Lua script: atomic check-and-decrement on Valkey
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

    public InventoryService(InventoryRepository inventoryRepository,
                            DomainEventPublisher publisher,
                            RedisTemplate<String, String> redisTemplate,
                            MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.publisher = publisher;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Attempt to soft-reserve inventory for all items in the order.
     * Uses Valkey atomic Lua script; falls back to ScyllaDB LWT on cache miss.
     *
     * @return InventoryReserved on success, InventoryInsufficient on shortage
     */
    public Object reserve(OrderReceived order) {
        String fcId = inventoryRepository.findBestFc(order);  // picks FC with most coverage
        List<Reservation> reservations = new ArrayList<>();
        List<Shortage> shortages = new ArrayList<>();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_LUA, Long.class);

        for (var item : order.getItems()) {
            String key = "inv:" + fcId + ":" + item.getSku();
            List<String> keys = List.of(key);
            Long result = redisTemplate.execute(script, keys, String.valueOf(item.getQuantity()));

            if (result == null || result < 0) {
                // Cache miss or insufficient — check ScyllaDB
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
            return event;
        }

        InventoryReserved event = InventoryReserved.newBuilder()
            .setOrderId(order.getOrderId().toString())
            .setFcId(fcId)
            .setReservations(reservations)
            .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).toEpochMilli())
            .setEventTime(Instant.now().toEpochMilli())
            .build();
        publisher.publish(INVENTORY_EVENTS_TOPIC, order.getOrderId().toString(), event);
        meterRegistry.counter("inventory.reservation.success").increment();
        return event;
    }
}
