package com.ciao.backend.dto.reservation;

import com.ciao.backend.entity.Reservation;
import com.ciao.backend.entity.ReservedSeat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class UserReservationDTO {
    private Integer id;
    private String bookingReference;
    private String status;
    private String passengerName;
    private String passengerPhone;
    private Integer scheduleId;
    private String origin;
    private String destination;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String busPlateNumber;
    private List<String> seatNumbers;
    private BigDecimal totalFare;
    private LocalDateTime createdAt;

    public UserReservationDTO() {}

    public UserReservationDTO(Reservation reservation, List<String> seatNumbers) {
        this.id = reservation.getId();
        this.bookingReference = "RES-" + reservation.getId();
        this.status = reservation.getStatus().name();
        this.passengerName = reservation.getPassengerName();
        this.passengerPhone = reservation.getPassengerPhone();
        this.totalFare = reservation.getTotalFare();
        this.createdAt = reservation.getCreatedAt();

        if (reservation.getSchedule() != null) {
            this.scheduleId = reservation.getSchedule().getId();
            this.departureTime = reservation.getSchedule().getDepartureTime();
            this.arrivalTime = reservation.getSchedule().getArrivalTime();

            if (reservation.getSchedule().getRoute() != null) {
                this.origin = reservation.getSchedule().getRoute().getOrigin();
                this.destination = reservation.getSchedule().getRoute().getDestination();
            }

            if (reservation.getSchedule().getBus() != null) {
                this.busPlateNumber = reservation.getSchedule().getBus().getPlateNumber();
            }
        }

        this.seatNumbers = seatNumbers;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }

    public Integer getScheduleId() { return scheduleId; }
    public void setScheduleId(Integer scheduleId) { this.scheduleId = scheduleId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getBusPlateNumber() { return busPlateNumber; }
    public void setBusPlateNumber(String busPlateNumber) { this.busPlateNumber = busPlateNumber; }

    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }

    public BigDecimal getTotalFare() { return totalFare; }
    public void setTotalFare(BigDecimal totalFare) { this.totalFare = totalFare; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
