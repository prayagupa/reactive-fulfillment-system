package com.shipping.carrier.application.query;

import com.shipping.cqrs.Query;
import java.util.List;

/**
 * Read-side intent: retrieve the full tracking timeline for a shipment.
 *
 * @param trackingNumber carrier-issued tracking number
 */
public record GetTrackingQuery(String trackingNumber) implements Query<List<TrackingEventView>> {}
