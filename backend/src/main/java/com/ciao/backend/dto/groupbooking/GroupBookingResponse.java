package com.ciao.backend.dto.groupbooking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GroupBookingResponse {
    private String eventType;
    private String preferredBusType;
    private String journeyDetails;
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getPreferredBusType() { return preferredBusType; }
    public void setPreferredBusType(String value) { preferredBusType = value; }
    public String getJourneyDetails() { return journeyDetails; }
    public void setJourneyDetails(String value) { journeyDetails = value; }
    private Integer id;
    private String bookingReference;
    private String customerName;
    private String customerPhone;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer passengerCount;
    private BigDecimal totalCost;
    private BigDecimal depositAmount;
    private String status;
    private String cancellationReason;
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    private Integer assignedBusId;
    private String guestAccessToken;
    private LocalDateTime createdAt;

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(Integer passengerCount) {
        this.passengerCount = passengerCount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAssignedBusId() {
        return assignedBusId;
    }

    public void setAssignedBusId(Integer assignedBusId) {
        this.assignedBusId = assignedBusId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGuestAccessToken() {
        return guestAccessToken;
    }

    public void setGuestAccessToken(String guestAccessToken) {
        this.guestAccessToken = guestAccessToken;
    }
}
