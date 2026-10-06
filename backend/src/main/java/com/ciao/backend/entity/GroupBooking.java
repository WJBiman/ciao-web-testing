package com.ciao.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_bookings")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class GroupBooking {
    @OneToOne @JoinColumn(name="booking_id",unique=true)
    private Booking booking;
    public Booking getBooking() { return booking; }
    public void setBooking(Booking value) { booking = value; }
    
    private String eventType;
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }

    @Column(name = "preferred_bus_type", length = 50)
    private String preferredBusType;
    @Column(name = "journey_details", length = 2000)
    private String journeyDetails;
    public String getPreferredBusType() { return preferredBusType; }
    public void setPreferredBusType(String value) { preferredBusType = value; }
    public String getJourneyDetails() { return journeyDetails; }
    public void setJourneyDetails(String value) { journeyDetails = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "customer_name", length = 100, nullable = false)
    private String customerName;

    @Column(name = "customer_phone", length = 15, nullable = false)
    private String customerPhone;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "passenger_count", nullable = false)
    private Integer passengerCount;

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private GroupBookingStatus status = GroupBookingStatus.PENDING_REVIEW;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_bus_id")
    private Bus assignedBus;

    @Column(name = "guest_access_token", length = 128, unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String guestAccessToken;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum GroupBookingStatus {
        PENDING_REVIEW,
        APPROVED,
        DEPOSIT_PAID,
        COMPLETED,
        CANCELLED
    }

    public GroupBooking() {
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(Integer passengerCount) {
        this.passengerCount = passengerCount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public GroupBookingStatus getStatus() {
        return status;
    }

    public void setStatus(GroupBookingStatus status) {
        this.status = status;
    }

    public Bus getAssignedBus() {
        return assignedBus;
    }

    public void setAssignedBus(Bus assignedBus) {
        this.assignedBus = assignedBus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGuestAccessToken() {
        return guestAccessToken;
    }

    public void setGuestAccessToken(String guestAccessToken) {
        this.guestAccessToken = guestAccessToken;
    }
}
