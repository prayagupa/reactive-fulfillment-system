package com.shipping.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Inbound payload for POST /api/v1/orders.
 * Immutable by design — use Java records so Jackson deserialises
 * via the canonical constructor and bean-validation runs on components.
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<ItemDto> items,
        @NotNull  @Valid AddressDto shippingAddress,
        String requestedDeliveryDate) {

    public record ItemDto(
            @NotBlank String sku,
            @Positive int quantity,
            @Positive double unitPrice) {}

    public record AddressDto(
            @NotBlank String line1,
            String line2,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank String postalCode,
            @NotBlank String countryCode) {}
}
