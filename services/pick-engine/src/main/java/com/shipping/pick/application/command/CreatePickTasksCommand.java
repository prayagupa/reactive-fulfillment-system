package com.shipping.pick.application.command;

import com.shipping.events.PickList;
import com.shipping.cqrs.Command;

/**
 * Write-side intent: materialise all PickTask rows from a wave-planner PickList event.
 *
 * @param pickList the PickList Avro event consumed from Kafka
 */
public record CreatePickTasksCommand(PickList pickList) implements Command {}
