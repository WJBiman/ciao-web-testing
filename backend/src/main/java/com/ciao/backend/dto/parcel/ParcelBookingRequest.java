package com.ciao.backend.dto.parcel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ParcelBookingRequest {

    @NotNull(message = "Bus ID is required")
    private Integer busId;

    @NotBlank(message = "Sender name is required")
    @Size(max = 100, message = "Sender name cannot exceed 100 characters")
    private String senderName;

    @NotBlank(message = "Sender phone is required")
    @Size(max = 15, message = "Sender phone cannot exceed 15 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(?:\\+94|0)?[0-9]{9,10}$", message = "Please enter a valid phone number")
    private String senderPhone;

    @NotBlank(message = "Receiver name is required")
    @Size(max = 100, message = "Receiver name cannot exceed 100 characters")
    private String receiverName;

    @NotBlank(message = "Receiver phone is required")
    @Size(max = 15, message = "Receiver phone cannot exceed 15 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(?:\\+94|0)?[0-9]{9,10}$", message = "Please enter a valid phone number")
    private String receiverPhone;

    @NotNull(message = "Parcel weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than zero")
    @DecimalMax(value = "999.99", message = "Weight exceeds maximum allowable limit")
    private BigDecimal weight;

    private Integer originBranchId;
    private Integer destinationBranchId;

    // Getters and Setters
    public Integer getOriginBranchId() {
        return originBranchId;
    }

    public void setOriginBranchId(Integer originBranchId) {
        this.originBranchId = originBranchId;
    }

    public Integer getDestinationBranchId() {
        return destinationBranchId;
    }

    public void setDestinationBranchId(Integer destinationBranchId) {
        this.destinationBranchId = destinationBranchId;
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
}
