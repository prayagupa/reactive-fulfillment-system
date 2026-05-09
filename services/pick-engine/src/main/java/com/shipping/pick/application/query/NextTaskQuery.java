package com.shipping.pick.application.query;

import com.shipping.cqrs.Query;
import com.shipping.pick.domain.model.PickTask;

/**
 * Read-side intent: fetch the next PENDING pick task for a warehouse associate.
 *
 * @param associateId the associate requesting work
 */
public record NextTaskQuery(String associateId) implements Query<PickTask> {}
