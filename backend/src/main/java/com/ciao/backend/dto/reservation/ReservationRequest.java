package com.ciao.backend.dto.reservation;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReservationRequest {

    @NotNull(message = "Schedule ID is required")
    private Integer scheduleId;

    @Size(max = 100, message = "Passenger name cannot exceed 100 characters")
    private String passengerName;

    @Size(max = 15, message = "Passenger phone cannot exceed 15 characters")
    private String passengerPhone;

    @NotNull(message = "Seat numbers list is required")
    @NotEmpty(message = "At least one seat must be selected")
    private List<String> seatNumbers;

    public Integer getScheduleId() { return scheduleId; }
    public void setScheduleId(Integer scheduleId) { this.scheduleId = scheduleId; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }

    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
}
