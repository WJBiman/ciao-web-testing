package com.ciao.backend.dto;

import com.ciao.backend.entity.Driver.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DriverRequest {

    @NotBlank(message = "Driver name is required")
    @Size(max = 100, message = "Driver name cannot exceed 100 characters")
    private String driverName;

    @NotBlank(message = "License number is required")
    @Size(max = 50, message = "License number cannot exceed 50 characters")
    private String licenseNumber;

    @NotBlank(message = "Contact number is required")
    @Size(max = 15, message = "Contact number cannot exceed 15 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(?:\\+94|0)?[0-9]{9,10}$", message = "Please enter a valid contact phone number")
    private String contactNumber;

    private DriverStatus status;

    public DriverRequest() {
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }
}
