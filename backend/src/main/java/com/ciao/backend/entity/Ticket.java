package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="tickets")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Ticket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @OneToOne @JoinColumn(name="reservation_id", nullable=false, unique=true)
    private Reservation reservation;
    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation value) { reservation = value; }
    @Column(nullable=false,unique=true)
    private String qrCode;
    public String getQrCode() { return qrCode; }
    public void setQrCode(String value) { qrCode = value; }
    
    private java.time.LocalDateTime issueDate;
    public java.time.LocalDateTime getIssueDate() { return issueDate; }
    public void setIssueDate(java.time.LocalDateTime value) { issueDate = value; }
    
    private String seatRange;
    public String getSeatRange() { return seatRange; }
    public void setSeatRange(String value) { seatRange = value; }
    
    private java.time.LocalDateTime checkedInAt;
    public java.time.LocalDateTime getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(java.time.LocalDateTime value) { checkedInAt = value; }
}

