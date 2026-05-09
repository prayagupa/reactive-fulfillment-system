package com.shipping.pick.application.query;

import com.shipping.cqrs.Query;

/**
 * Read-side intent: check whether every task in a pick list has been PICKED.
 *
 * @param pickListId the pick list to evaluate
 */
public record PickListStatusQuery(String pickListId) implements Query<Boolean> {}
