package com.ciao.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cancellation_requests")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CancellationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User requestedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjudicated_by_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StaffProfile adjudicatedBy;

    @Column
    private LocalDateTime adjudicatedAt;

    @Column(length = 500)
    private String adjudicationNotes;

    @Column(name = "requested_at", nullable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }

    public CancellationRequest() {}

    public CancellationRequest(Reservation reservation, User requestedBy, String reason) {
        this.reservation = reservation;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.status = RequestStatus.PENDING;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

    public User getRequestedBy() { return requestedBy; }
    public void setRequestedBy(User requestedBy) { this.requestedBy = requestedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public StaffProfile getAdjudicatedBy() { return adjudicatedBy; }
    public void setAdjudicatedBy(StaffProfile adjudicatedBy) { this.adjudicatedBy = adjudicatedBy; }

    public LocalDateTime getAdjudicatedAt() { return adjudicatedAt; }
    public void setAdjudicatedAt(LocalDateTime adjudicatedAt) { this.adjudicatedAt = adjudicatedAt; }

    public String getAdjudicationNotes() { return adjudicationNotes; }
    public void setAdjudicationNotes(String adjudicationNotes) { this.adjudicationNotes = adjudicationNotes; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
