package com.shipping.order.infrastructure.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.shipping.order.domain.model.Order;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
                order.getOrderId(),
                order.getIdempotencyKey(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getFcId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
            );
            return session.execute(bound).wasApplied();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Optional<Order>> findById(UUID orderId) {
        return Mono.fromCallable(() -> {
            var row = session.execute(findByIdStmt.bind(orderId)).one();
            if (row == null) return Optional.<Order>empty();
            Order o = new Order();
            o.setOrderId(row.getUuid("order_id"));
            o.setIdempotencyKey(row.getString("idempotency_key"));
            o.setCustomerId(row.getString("customer_id"));
            o.setStatus(Order.Status.valueOf(row.getString("status")));
            o.setFcId(row.getString("fc_id"));
            o.setCreatedAt(row.getInstant("created_at"));
            o.setUpdatedAt(row.getInstant("updated_at"));
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
