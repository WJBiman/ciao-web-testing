package com.ciao.backend.dto.parcel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ParcelResponse {
    private Integer id;
    private String trackingId;
    private Integer busId;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private BigDecimal weight;
    private BigDecimal totalFee;
    private String status;
    private LocalDateTime createdAt;
    private String busPlateNumber;
    private Integer originBranchId;
    private String originBranchLocation;
    private Integer destinationBranchId;
    private String destinationBranchLocation;

    public String getBusPlateNumber() { return busPlateNumber; }
    public void setBusPlateNumber(String busPlateNumber) { this.busPlateNumber = busPlateNumber; }

    public Integer getOriginBranchId() { return originBranchId; }
    public void setOriginBranchId(Integer originBranchId) { this.originBranchId = originBranchId; }

    public String getOriginBranchLocation() { return originBranchLocation; }
    public void setOriginBranchLocation(String originBranchLocation) { this.originBranchLocation = originBranchLocation; }

    public Integer getDestinationBranchId() { return destinationBranchId; }
    public void setDestinationBranchId(Integer destinationBranchId) { this.destinationBranchId = destinationBranchId; }

    public String getDestinationBranchLocation() { return destinationBranchLocation; }
    public void setDestinationBranchLocation(String destinationBranchLocation) { this.destinationBranchLocation = destinationBranchLocation; }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public Integer getBusId() {
        return busId;
    }

    public void setBusId(Integer busId) {
        this.busId = busId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(BigDecimal totalFee) {
        this.totalFee = totalFee;
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
}
