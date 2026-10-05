package com.ciao.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Payment {
    @ManyToOne @JoinColumn(name="booking_id")
    private Booking booking;
    public Booking getBooking() { return booking; }
    public void setBooking(Booking value) { booking = value; }
    
    private String paymentType;
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String value) { paymentType = value; }
    @ManyToOne @JoinColumn(name="verified_by_id")
    private StaffProfile verifiedBy;
    public StaffProfile getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(StaffProfile value) { verifiedBy = value; }
    
    private java.time.LocalDateTime verifiedAt;
    public java.time.LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(java.time.LocalDateTime value) { verifiedAt = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentMethod {
        CARD, BANK_TRANSFER, CASH
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED
    }

    public Payment() {}

    public Payment(Reservation reservation, BigDecimal amount, PaymentMethod paymentMethod, PaymentStatus status, String transactionId) {
        this.reservation = reservation;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.transactionId = transactionId;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

