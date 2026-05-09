package com.shipping.cqrs;

/** Executes a single {@link Query} type and returns a result of type {@code R}. */
@FunctionalInterface
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
