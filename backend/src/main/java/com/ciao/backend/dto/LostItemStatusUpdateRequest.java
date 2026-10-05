package com.ciao.backend.dto;

import com.ciao.backend.entity.LostItemStatus;
import jakarta.validation.constraints.NotNull;

public class LostItemStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private LostItemStatus status;

    public LostItemStatusUpdateRequest() {
    }

    public LostItemStatus getStatus() {
        return status;
    }

    public void setStatus(LostItemStatus status) {
        this.status = status;
    }
}
