package com.shipping.cqrs;

/** Executes a single {@link Command} type and returns a result of type {@code R}. */
@FunctionalInterface
public interface CommandHandler<C extends Command, R> {
    R handle(C command);
}
