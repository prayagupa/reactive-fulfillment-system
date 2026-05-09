package com.shipping.inventory.application;

/**
 * @deprecated Superseded by CQRS split:
 * <ul>
 *   <li>Write side: {@link com.shipping.inventory.application.command.ReserveInventoryCommandHandler}</li>
 *   <li>Read  side: {@link com.shipping.inventory.application.query.GetStockQueryHandler}</li>
 * </ul>
 * This class is retained only to preserve git history; it is no longer wired into the application context.
 */
@Deprecated(forRemoval = true)
public final class InventoryService {}
