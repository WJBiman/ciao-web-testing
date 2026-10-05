package com.ciao.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LostItemRequest {

    @NotBlank(message = "Item description is required")
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String itemDescription;

    @NotBlank(message = "Reporter name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String reportedByName;

    @NotBlank(message = "Reporter phone is required")
    @Size(max = 15, message = "Phone must not exceed 15 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(?:\\+94|0)?[0-9]{9,10}$", message = "Please enter a valid reporter phone number")
    private String reportedByPhone;

    // Optional — nullable in schema
    private Integer busId;
    private Integer routeId;

    public LostItemRequest() {
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public String getReportedByName() {
        return reportedByName;
    }

    public void setReportedByName(String reportedByName) {
        this.reportedByName = reportedByName;
    }

    public String getReportedByPhone() {
        return reportedByPhone;
    }

    public void setReportedByPhone(String reportedByPhone) {
        this.reportedByPhone = reportedByPhone;
    }

    public Integer getBusId() {
        return busId;
    }

    public void setBusId(Integer busId) {
        this.busId = busId;
    }

    public Integer getRouteId() {
        return routeId;
    }

    public void setRouteId(Integer routeId) {
        this.routeId = routeId;
    }
}
