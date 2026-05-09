package com.shipping.order.domain.model;

/** Shipping address value object. Immutable record. */
public record Address(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode) {}
