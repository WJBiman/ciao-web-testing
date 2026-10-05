package com.ciao.backend.dto.reservation;

import com.ciao.backend.entity.Reservation;
import java.math.BigDecimal;
import java.util.List;

public class ReservationResponse {
    private Integer ticketId;
    private String qrCode;
    private java.time.LocalDateTime issueDate;
    public Integer getTicketId() { return ticketId; }
    public void setTicketId(Integer value) { ticketId=value; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String value) { qrCode=value; }
    public java.time.LocalDateTime getIssueDate() { return issueDate; }
    public void setIssueDate(java.time.LocalDateTime value) { issueDate=value; }
    private Integer id;
    private Integer scheduleId;
    private String passengerName;
    private BigDecimal totalFare;
    private String status;
    private String guestToken; // Only populated for guests
    private List<String> seatNumbers;

    public ReservationResponse() {}

    public ReservationResponse(Reservation reservation, List<String> seatNumbers, String guestToken) {
        this.id = reservation.getId();
        this.scheduleId = reservation.getSchedule().getId();
        this.passengerName = reservation.getPassengerName();
        this.totalFare = reservation.getTotalFare();
        this.status = reservation.getStatus().name();
        this.seatNumbers = seatNumbers;
        this.guestToken = guestToken;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getScheduleId() { return scheduleId; }
    public void setScheduleId(Integer scheduleId) { this.scheduleId = scheduleId; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public BigDecimal getTotalFare() { return totalFare; }
    public void setTotalFare(BigDecimal totalFare) { this.totalFare = totalFare; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGuestToken() { return guestToken; }
    public void setGuestToken(String guestToken) { this.guestToken = guestToken; }

    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
}

