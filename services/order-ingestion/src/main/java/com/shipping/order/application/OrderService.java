package com.shipping.order.application;

/**
 * @deprecated Superseded by CQRS split:
 * <ul>
 *   <li>Write side: {@link com.shipping.order.application.command.CreateOrderCommandHandler}</li>
 *   <li>Read  side: {@link com.shipping.order.application.query.GetOrderQueryHandler}</li>
 * </ul>
 * This class is retained only to preserve git history; it is no longer wired into the application context.
 */
@Deprecated(forRemoval = true)
public final class OrderService {}
