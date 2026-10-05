package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="bookings")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Booking {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="customer_id")
    private CustomerProfile customer;
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile value) { customer = value; }
    @ManyToOne @JoinColumn(name="schedule_id")
    private Schedule schedule;
    public Schedule getSchedule() { return schedule; }
    public void setSchedule(Schedule value) { schedule = value; }
    @Column(nullable=false)
    private String bookingType;
    public String getBookingType() { return bookingType; }
    public void setBookingType(String value) { bookingType = value; }
    @Column(nullable=false)
    private String bookingStatus;
    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String value) { bookingStatus = value; }
    @Column(precision=12,scale=2)
    private java.math.BigDecimal totalFare;
    public java.math.BigDecimal getTotalFare() { return totalFare; }
    public void setTotalFare(java.math.BigDecimal value) { totalFare = value; }
    
    private java.time.LocalDateTime bookingDate;
    public java.time.LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(java.time.LocalDateTime value) { bookingDate = value; }
}

