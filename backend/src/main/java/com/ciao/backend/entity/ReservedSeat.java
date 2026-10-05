package com.ciao.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reserved_seats")
public class ReservedSeat {
    @ManyToOne @JoinColumn(name="seat_id")
    private BusSeat seat;
    public BusSeat getSeat() { return seat; }
    public void setSeat(BusSeat value) { seat = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SeatStatus status = SeatStatus.LOCKED;

    public enum SeatStatus {
        LOCKED, BOOKED
    }

    public ReservedSeat() {}

    public ReservedSeat(Reservation reservation, String seatNumber, LocalDateTime lockExpiresAt, SeatStatus status) {
        this.reservation = reservation;
        this.seatNumber = seatNumber;
        this.lockExpiresAt = lockExpiresAt;
        this.status = status;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public LocalDateTime getLockExpiresAt() { return lockExpiresAt; }
    public void setLockExpiresAt(LocalDateTime lockExpiresAt) { this.lockExpiresAt = lockExpiresAt; }

    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
}

