package com.shipping.inventory.application.command;

import com.shipping.cqrs.AggregateResult;
import com.shipping.cqrs.CommandHandler;
import com.shipping.events.InventoryInsufficient;
import com.shipping.events.InventoryReserved;
import com.shipping.events.OrderReceived;
import com.shipping.events.Reservation;
import com.shipping.events.Shortage;
import com.shipping.inventory.domain.event.InventoryDomainEvent;
import com.shipping.inventory.domain.event.InventoryInsufficientEvent;
import com.shipping.inventory.domain.event.InventoryReservedEvent;
import com.shipping.inventory.domain.model.StockLedger;
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
 *
 * <p>Four-step thin coordinator:
 * <ol>
 *   <li>Load — fetch {@link StockLedger} for each SKU</li>
 *   <li>Decide — call {@code ledger.reserve()} to get domain events</li>
 *   <li>Save — atomically persist via Valkey Lua fast path + ScyllaDB LWT fallback</li>
 *   <li>Publish — map per-item domain events → one Avro envelope per order</li>
 * </ol>
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

        List<InventoryReservedEvent> reserved = new ArrayList<>();
        List<InventoryInsufficientEvent> insufficient = new ArrayList<>();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_LUA, Long.class);

        for (var item : order.getItems()) {
            String sku = item.getSku().toString();
            int qty  = item.getQuantity();

            // ── Step 1: Load aggregate ──────────────────────────────────────
            StockLedger ledger = inventoryRepository.findStockLedger(fcId, sku);

            // ── Step 2: Domain decision ─────────────────────────────────────
            AggregateResult<StockLedger, InventoryDomainEvent> result = ledger.reserve(qty);

            // ── Step 3: Persist (only on success path) ──────────────────────
            switch (result.events().getFirst()) {
                case InventoryReservedEvent e -> atomicReserve(script, fcId, sku, qty);
                case InventoryInsufficientEvent ignored -> { /* nothing to persist */ }
            }

            // ── Collect per-item domain events ──────────────────────────────
            result.events().forEach(e -> {
                switch (e) {
                    case InventoryReservedEvent ev   -> reserved.add(ev);
                    case InventoryInsufficientEvent ev -> insufficient.add(ev);
                }
            });
        }

        // ── Step 4: Publish one Avro envelope per order ─────────────────────
        if (!insufficient.isEmpty()) {
            publishInsufficient(order, insufficient);
        } else {
            publishReserved(order, fcId, reserved);
        }

        return null;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Attempts Valkey Lua fast path; falls back to ScyllaDB LWT on cache miss.
     */
    private void atomicReserve(DefaultRedisScript<Long> script, String fcId, String sku, int qty) {
        String key = "inv:" + fcId + ":" + sku;
        Long luaResult = redisTemplate.execute(script, List.of(key), String.valueOf(qty));
        if (luaResult == null || luaResult < 0) {
            // Cache miss — fall through to ScyllaDB LWT
            inventoryRepository.softReserve(fcId, sku, qty);
        }
    }

    private void publishReserved(OrderReceived order, String fcId,
                                 List<InventoryReservedEvent> events) {
        List<Reservation> avroReservations = events.stream()
            .map(e -> Reservation.newBuilder()
                .setSku(e.sku())
                .setQuantity(e.quantityReserved())
                .setBinLocation(null)
                .build())
            .toList();

        InventoryReserved avro = InventoryReserved.newBuilder()
            .setOrderId(order.getOrderId().toString())
            .setFcId(fcId)
            .setReservations(avroReservations)
            .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
            .setEventTime(Instant.now())
            .build();

        publisher.publish(INVENTORY_EVENTS_TOPIC, order.getOrderId().toString(), avro);
        meterRegistry.counter("inventory.reservation.success").increment();
        log.info("Inventory reserved orderId={} fcId={} items={}", order.getOrderId(), fcId, events.size());
    }

    private void publishInsufficient(OrderReceived order,
                                     List<InventoryInsufficientEvent> events) {
        List<Shortage> avroShortages = events.stream()
            .map(e -> Shortage.newBuilder()
                .setSku(e.sku())
                .setRequested(e.requested())
                .setAvailable(e.available())
                .build())
            .toList();

        InventoryInsufficient avro = InventoryInsufficient.newBuilder()
            .setOrderId(order.getOrderId().toString())
            .setShortages(avroShortages)
            .setEventTime(Instant.now())
            .build();

        publisher.publish(INVENTORY_EVENTS_TOPIC, order.getOrderId().toString(), avro);
        meterRegistry.counter("inventory.reservation.insufficient").increment();
        log.warn("Inventory insufficient orderId={} shortages={}", order.getOrderId(), events.size());
    }
}
