package com.ciao.backend.dto.parcel;

import java.time.LocalDateTime;

public class ParcelTrackingResponse {
    private String trackingId;
    private String status;
    private LocalDateTime createdAt;
    // Bus details for tracking context
    private String busPlateNumber;
    private String routeOrigin;
    private String routeDestination;

    private java.math.BigDecimal weight;
    private java.math.BigDecimal totalFee;
    private String senderName;
    private String receiverName;
    private String originBranchLocation;
    private String destinationBranchLocation;

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getBusPlateNumber() {
        return busPlateNumber;
    }

    public void setBusPlateNumber(String busPlateNumber) {
        this.busPlateNumber = busPlateNumber;
    }

    public String getRouteOrigin() {
        return routeOrigin;
    }

    public void setRouteOrigin(String routeOrigin) {
        this.routeOrigin = routeOrigin;
    }

    public String getRouteDestination() {
        return routeDestination;
    }

    public void setRouteDestination(String routeDestination) {
        this.routeDestination = routeDestination;
    }

    public java.math.BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(java.math.BigDecimal weight) {
        this.weight = weight;
    }

    public java.math.BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(java.math.BigDecimal totalFee) {
        this.totalFee = totalFee;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getOriginBranchLocation() {
        return originBranchLocation;
    }

    public void setOriginBranchLocation(String originBranchLocation) {
        this.originBranchLocation = originBranchLocation;
    }

    public String getDestinationBranchLocation() {
        return destinationBranchLocation;
    }

    public void setDestinationBranchLocation(String destinationBranchLocation) {
        this.destinationBranchLocation = destinationBranchLocation;
    }
}
