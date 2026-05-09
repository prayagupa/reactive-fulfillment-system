package com.shipping.order.api.dto;

public class CreateOrderResponse {
    private String orderId;
    private String status;

    public CreateOrderResponse() {}
    public CreateOrderResponse(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
