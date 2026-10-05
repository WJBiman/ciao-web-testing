package com.ciao.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ScheduleRequest {

    @NotNull(message = "Route ID is required")
    private Integer routeId;

    private Integer busId;

    private Integer driverId;

    @NotNull(message = "Departure time is required")
    private LocalDateTime departureTime;

    @NotNull(message = "Arrival time is required")
    private LocalDateTime arrivalTime;

    @Pattern(regexp = "^(SCHEDULED|IN_TRANSIT|COMPLETED|CANCELLED)$", message = "Status must be SCHEDULED, IN_TRANSIT, COMPLETED, or CANCELLED")
    private String status;

    private Boolean repeatDaily;

    private LocalDate repeatUntil;

    public Integer getRouteId() {
        return routeId;
    }
    public void setRouteId(Integer routeId) {
        this.routeId = routeId;
    }
    public Integer getBusId() {
        return busId;
    }
    public void setBusId(Integer busId) {
        this.busId = busId;
    }
    public Integer getDriverId() {
        return driverId;
    }
    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
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
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getRepeatDaily() {
        return repeatDaily;
    }

    public void setRepeatDaily(Boolean repeatDaily) {
        this.repeatDaily = repeatDaily;
    }

    public LocalDate getRepeatUntil() {
        return repeatUntil;
    }

    public void setRepeatUntil(LocalDate repeatUntil) {
        this.repeatUntil = repeatUntil;
    }
}
