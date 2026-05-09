package com.shipping.order.infrastructure.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.shipping.order.domain.model.Order;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ScyllaDB repository for orders.
 *
 * Table DDL (managed by Liquibase migration scripts in /infra/scylladb/):
 * <pre>
 *   CREATE TABLE orders (
 *     order_id        UUID,
 *     idempotency_key TEXT,
 *     customer_id     TEXT,
 *     status          TEXT,
 *     fc_id           TEXT,
 *     payload         TEXT,   -- JSON blob for items + address
 *     created_at      TIMESTAMP,
 *     updated_at      TIMESTAMP,
 *     PRIMARY KEY (order_id)
 *   ) WITH default_time_to_live = 0;
 *
 *   CREATE INDEX ON orders (idempotency_key);
 * </pre>
 */
@Repository
public class OrderRepository {

    private final CqlSession session;
    private PreparedStatement insertStmt;
    private PreparedStatement findByIdStmt;
    private PreparedStatement findByIdempotencyKeyStmt;
    private PreparedStatement updateStatusStmt;

    public OrderRepository(CqlSession session) {
        this.session = session;
    }

    @PostConstruct
    void prepareStatements() {
        insertStmt = session.prepare(
            "INSERT INTO orders (order_id, idempotency_key, customer_id, status, fc_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS");

        findByIdStmt = session.prepare(
            "SELECT * FROM orders WHERE order_id = ?");

        findByIdempotencyKeyStmt = session.prepare(
            "SELECT * FROM orders WHERE idempotency_key = ? ALLOW FILTERING");

        updateStatusStmt = session.prepare(
            "UPDATE orders SET status = ?, fc_id = ?, updated_at = ? WHERE order_id = ?");
    }

    public Mono<Boolean> save(Order order) {
        return Mono.fromCallable(() -> {
            BoundStatement bound = insertStmt.bind(
                order.orderId(),
                order.idempotencyKey(),
                order.customerId(),
                order.status().name(),
                order.fcId(),
                order.createdAt(),
                order.updatedAt()
            );
            return session.execute(bound).wasApplied();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Optional<Order>> findById(UUID orderId) {
        return Mono.fromCallable(() -> {
            var row = session.execute(findByIdStmt.bind(orderId)).one();
            if (row == null) return Optional.<Order>empty();
            Order o = new Order(
                row.getUuid("order_id"),
                row.getString("idempotency_key"),
                row.getString("customer_id"),
                List.of(),   // items persisted separately as JSON payload
                null,        // shippingAddress persisted separately as JSON payload
                null,
                Order.Status.valueOf(row.getString("status")),
                row.getString("fc_id"),
                row.getInstant("created_at"),
                row.getInstant("updated_at"));
            return Optional.of(o);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> findByIdempotencyKey(String key) {
        return Mono.fromCallable(() -> {
            var row = session.execute(findByIdempotencyKeyStmt.bind(key)).one();
            return row != null;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
