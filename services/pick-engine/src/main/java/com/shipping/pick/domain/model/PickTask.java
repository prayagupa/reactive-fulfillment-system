package com.shipping.pick.domain.model;

public class PickTask {

    public enum Status { PENDING, ASSIGNED, PICKED, SHORT }

    private String pickListId;
    private String orderId;
    private int itemSeq;
    private String sku;
    private int quantityRequired;
    private String binLocation;
    private String pickedBy;
    private Status status;

    public String getPickListId() { return pickListId; }
    public void setPickListId(String pickListId) { this.pickListId = pickListId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getItemSeq() { return itemSeq; }
    public void setItemSeq(int itemSeq) { this.itemSeq = itemSeq; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantityRequired() { return quantityRequired; }
    public void setQuantityRequired(int quantityRequired) { this.quantityRequired = quantityRequired; }
    public String getBinLocation() { return binLocation; }
    public void setBinLocation(String binLocation) { this.binLocation = binLocation; }
    public String getPickedBy() { return pickedBy; }
    public void setPickedBy(String pickedBy) { this.pickedBy = pickedBy; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
