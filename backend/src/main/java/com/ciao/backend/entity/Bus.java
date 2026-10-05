package com.ciao.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "buses")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Bus {
    
    private String busType;
    public String getBusType() { return busType; }
    public void setBusType(String value) { busType = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne @JoinColumn(name="registered_by_id")
    private StaffProfile registeredBy;
    public StaffProfile getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(StaffProfile value) { registeredBy = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plate_number", length = 15, nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "amenities")
    private String amenities;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BusStatus status = BusStatus.ACTIVE;

    public enum BusStatus {
        ACTIVE, MAINTENANCE, RETIRED
    }

    public Bus() {
    }

    public Bus(String plateNumber, Integer capacity, String amenities, BusStatus status) {
        this.plateNumber = plateNumber;
        this.capacity = capacity;
        this.amenities = amenities;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
