package com.ciao.backend.dto.parcel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ParcelStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(PENDING|IN_TRANSIT|DELIVERED|RETURNED)$", message = "Status must be PENDING, IN_TRANSIT, DELIVERED, or RETURNED")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
