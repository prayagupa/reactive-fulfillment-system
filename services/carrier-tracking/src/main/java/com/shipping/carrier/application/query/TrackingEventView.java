package com.shipping.carrier.application.query;

/**
 * Read model: a single carrier tracking event in the shipment timeline.
 *
 * @param eventTime   ISO-8601 timestamp string
 * @param status      tracking status (e.g. IN_TRANSIT, DELIVERED)
 * @param location    human-readable location string, may be empty
 * @param description narrative description of the event
 */
public record TrackingEventView(
        String eventTime,
        String status,
        String location,
        String description) {}
