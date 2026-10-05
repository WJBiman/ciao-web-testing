package com.ciao.backend.dto.groupbooking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class GroupBookingStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(PENDING_REVIEW|APPROVED|DEPOSIT_PAID|COMPLETED|CANCELLED)$", 
             message = "Status must be PENDING_REVIEW, APPROVED, DEPOSIT_PAID, COMPLETED, or CANCELLED")
    private String status;

    private Integer assignedBusId;

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
}
