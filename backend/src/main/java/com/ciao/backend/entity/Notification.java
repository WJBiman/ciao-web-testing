package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="notifications")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="user_id", nullable=false)
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    @ManyToOne @JoinColumn(name="booking_id")
    private Booking booking;
    public Booking getBooking() { return booking; }
    public void setBooking(Booking value) { booking = value; }
    
    @Column(nullable=false)
    private String title = "Notification";
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }

    private String notificationType;
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String value) { notificationType = value; }
    @Column(length=1000)
    private String message;
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    
    private java.time.LocalDateTime sentAt;
    public java.time.LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(java.time.LocalDateTime value) { sentAt = value; }
    
    private java.time.LocalDateTime readAt;
    public java.time.LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(java.time.LocalDateTime value) { readAt = value; }

    @Column(name="read_status")
    private Boolean readStatus = false;
    public Boolean getReadStatus() { return readStatus; }
    public void setReadStatus(Boolean value) { readStatus = value; }

    @Column(name="created_at")
    private java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt = value; }

    @PrePersist
    public void onPrePersist() {
        if (title == null || title.isBlank()) title = "Notification";
        if (createdAt == null) createdAt = java.time.LocalDateTime.now();
        if (sentAt == null) sentAt = java.time.LocalDateTime.now();
        if (readStatus == null) readStatus = false;
    }
}

