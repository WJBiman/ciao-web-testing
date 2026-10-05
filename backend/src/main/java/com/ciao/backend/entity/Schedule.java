package com.ciao.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "licenseNumber", "contactNumber", "assignedBus"})
    private Driver driver;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(name = "repeat_daily", nullable = false)
    private boolean repeatDaily = false;

    @Column(name = "repeat_until")
    private LocalDate repeatUntil;

    @Column(name = "recurrence_parent_id")
    private Integer recurrenceParentId;

    @Column(name = "is_charter", nullable = false)
    private boolean isCharter = false;

    public enum ScheduleStatus {
        SCHEDULED, IN_TRANSIT, COMPLETED, CANCELLED
    }

    public Schedule() {
    }

    public Schedule(Route route, Bus bus, Driver driver, LocalDateTime departureTime, LocalDateTime arrivalTime, ScheduleStatus status) {
        this.route = route;
        this.bus = bus;
        this.driver = driver;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Bus getBus() {
        return bus;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public boolean isRepeatDaily() {
        return repeatDaily;
    }

    public void setRepeatDaily(boolean repeatDaily) {
        this.repeatDaily = repeatDaily;
    }

    public LocalDate getRepeatUntil() {
        return repeatUntil;
    }

    public void setRepeatUntil(LocalDate repeatUntil) {
        this.repeatUntil = repeatUntil;
    }

    public Integer getRecurrenceParentId() {
        return recurrenceParentId;
    }

    public void setRecurrenceParentId(Integer recurrenceParentId) {
        this.recurrenceParentId = recurrenceParentId;
    }

    public boolean isCharter() {
        return isCharter;
    }

    public void setCharter(boolean charter) {
        isCharter = charter;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("effectiveSeatFare")
    public java.math.BigDecimal getEffectiveSeatFare() {
        return com.ciao.backend.service.NTCFareCalculator.calculateSeatFare(this);
    }
}

