package com.shipping.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class CreateOrderRequest {

    @NotBlank
    private String customerId;

    @NotEmpty
    @Valid
    private List<ItemDto> items;

    @NotNull
    @Valid
    private AddressDto shippingAddress;

    private String requestedDeliveryDate;

    public static class ItemDto {
        @NotBlank public String sku;
        @Positive  public int quantity;
        @Positive  public double unitPrice;
    }

    public static class AddressDto {
        @NotBlank public String line1;
        public String line2;
        @NotBlank public String city;
        @NotBlank public String state;
        @NotBlank public String postalCode;
        @NotBlank public String countryCode;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public List<ItemDto> getItems() { return items; }
    public void setItems(List<ItemDto> items) { this.items = items; }
    public AddressDto getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(AddressDto shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(String requestedDeliveryDate) { this.requestedDeliveryDate = requestedDeliveryDate; }
}
