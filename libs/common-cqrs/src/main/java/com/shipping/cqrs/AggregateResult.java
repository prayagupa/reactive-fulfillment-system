package com.shipping.cqrs;

import java.util.List;

/**
 * Return type for every aggregate domain method.
 * <p>
 * An aggregate method (e.g. {@code Order#receive()},
 * {@code PickList#confirmScan(…)}) must <strong>never</strong> mutate state
 * in-place.  Instead it returns an {@code AggregateResult} that carries:
 * <ul>
 *   <li>{@link #state()} — the new (immutable) aggregate state to be
 *       persisted by the command handler via the repository.</li>
 *   <li>{@link #events()} — the ordered list of domain events to be mapped
 *       to Avro and published to Kafka by the command handler <em>after</em>
 *       the state has been durably persisted.</li>
 * </ul>
 *
 * <h3>Command handler contract (load → call → save → publish)</h3>
 * <pre>{@code
 * var result  = aggregate.someMethod(args);          // 1. call domain method
 * repository.save(result.state());                   // 2. persist new state
 * result.events().forEach(publisher::publishKafka);  // 3. publish events
 * }</pre>
 *
 * @param <A> aggregate root type
 * @param <E> sealed domain-event type for this bounded context
 */
public record AggregateResult<A, E extends DomainEvent>(A state, List<E> events) {

    /** Convenience factory for a single-event result. */
    public static <A, E extends DomainEvent> AggregateResult<A, E> of(A state, E event) {
        return new AggregateResult<>(state, List.of(event));
    }

    /** Convenience factory for a multi-event result. */
    public static <A, E extends DomainEvent> AggregateResult<A, E> of(A state, List<E> events) {
        return new AggregateResult<>(state, List.copyOf(events));
    }
}
