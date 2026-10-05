package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="bus_seats", uniqueConstraints=@UniqueConstraint(columnNames={"bus_id","seat_number"}))
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class BusSeat {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="bus_id", nullable=false)
    private Bus bus;
    public Bus getBus() { return bus; }
    public void setBus(Bus value) { bus = value; }
    @Column(nullable=false)
    private String seatNumber;
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String value) { seatNumber = value; }
    @Column(nullable=false)
    private String seatStatus;
    public String getSeatStatus() { return seatStatus; }
    public void setSeatStatus(String value) { seatStatus = value; }
}

