package com.ciao.backend.dto;

import com.ciao.backend.entity.Bus.BusStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class BusRequest {

    @NotBlank(message = "Plate number is required")
    @Size(max = 15, message = "Plate number cannot exceed 15 characters")
    private String plateNumber;

    @NotNull(message = "Capacity is required")
    @Positive(message = "Bus capacity must be greater than zero")
    private Integer capacity;

    @Size(max = 255, message = "Amenities cannot exceed 255 characters")
    private String amenities;

    private BusStatus status;

    public BusRequest() {
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = amenities;
    }

    public BusStatus getStatus() {
        return status;
    }

    public void setStatus(BusStatus status) {
        this.status = status;
    }
}
