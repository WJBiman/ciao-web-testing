package com.ciao.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class RouteRequest {

    @NotBlank(message = "Route origin is required")
    @Size(max = 100, message = "Origin cannot exceed 100 characters")
    private String origin;

    @NotBlank(message = "Route destination is required")
    @Size(max = 100, message = "Destination cannot exceed 100 characters")
    private String destination;

    @NotNull(message = "Base fare is required")
    @DecimalMin(value = "1.00", message = "Base fare must be at least 1.00 LKR")
    private BigDecimal baseFare;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status must be either ACTIVE or INACTIVE")
    private String status;

    public String getOrigin() {
        return origin;
    }
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    public String getDestination() {
        return destination;
    }
    public void setDestination(String destination) {
        this.destination = destination;
    }
    public BigDecimal getBaseFare() {
        return baseFare;
    }
    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
