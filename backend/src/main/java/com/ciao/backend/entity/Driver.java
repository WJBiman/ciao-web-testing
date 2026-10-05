package com.ciao.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "drivers")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Driver {
    @ManyToOne @JoinColumn(name="assigned_bus_id")
    private Bus assignedBus;
    public Bus getAssignedBus() { return assignedBus; }
    public void setAssignedBus(Bus value) { assignedBus = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "driver_name", length = 100, nullable = false)
    private String driverName;

    @Column(name = "license_number", length = 50, nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "contact_number", length = 15, nullable = false)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DriverStatus status = DriverStatus.AVAILABLE;

    public enum DriverStatus {
        AVAILABLE, ON_DUTY, ON_LEAVE, RETIRED
    }

    public Driver() {
    }

    public Driver(String driverName, String licenseNumber, String contactNumber, DriverStatus status) {
        this.driverName = driverName;
        this.licenseNumber = licenseNumber;
        this.contactNumber = contactNumber;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
