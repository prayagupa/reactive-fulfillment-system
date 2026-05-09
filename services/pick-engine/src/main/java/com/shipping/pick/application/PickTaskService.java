package com.shipping.pick.application;

/**
 * @deprecated Superseded by CQRS split:
 * <ul>
 *   <li>Write side: {@link com.shipping.pick.application.command.CreatePickTasksCommandHandler}</li>
 *   <li>Write side: {@link com.shipping.pick.application.command.ConfirmScanCommandHandler}</li>
 *   <li>Read  side: {@link com.shipping.pick.application.query.NextTaskQueryHandler}</li>
 *   <li>Read  side: {@link com.shipping.pick.application.query.PickListStatusQueryHandler}</li>
 * </ul>
 * This class is retained only to preserve git history; it is no longer wired into the application context.
 */
@Deprecated(forRemoval = true)
public final class PickTaskService {}
