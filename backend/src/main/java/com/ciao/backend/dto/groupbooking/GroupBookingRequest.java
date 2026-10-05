package com.ciao.backend.dto.groupbooking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GroupBookingRequest {

    @Size(max = 255)
    private String eventType;
    @Size(max = 50)
    private String preferredBusType;
    @Size(max = 2000)
    private String journeyDetails;

    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getPreferredBusType() { return preferredBusType; }
    public void setPreferredBusType(String value) { preferredBusType = value; }
    public String getJourneyDetails() { return journeyDetails; }
    public void setJourneyDetails(String value) { journeyDetails = value; }

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Customer phone number is required")
    @Size(max = 15, message = "Phone number cannot exceed 15 characters")
    private String customerPhone;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    @NotNull(message = "End date is required")
    private LocalDateTime endDate;

    @NotNull(message = "Passenger count is required")
    @Positive(message = "Passenger count must be a positive number")
    private Integer passengerCount;

    @NotNull(message = "Total cost is required")
    @DecimalMin(value = "0.01", message = "Total cost must be greater than zero")
    private BigDecimal totalCost;

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0.00", message = "Deposit amount cannot be negative")
    private BigDecimal depositAmount;

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
}
