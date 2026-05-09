package com.shipping.cqrs;

/**
 * Marker interface for <em>internal</em> domain events raised by aggregate
 * methods.
 * <p>
 * These are in-process records that travel from an aggregate method back to
 * the command handler; they are <strong>not</strong> the Avro records
 * published to Kafka.  The command handler is responsible for mapping each
 * {@code DomainEvent} to its corresponding Avro message and publishing it via
 * {@link com.shipping.kafka.producer.DomainEventPublisher}.
 * <p>
 * Sealed sub-interfaces (one per bounded context) restrict which concrete
 * event types may exist inside that context.
 *
 * @see AggregateResult
 */
public interface DomainEvent {}
