package com.ciao.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "routes")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Route {
    @Column(precision=8,scale=2)
    private java.math.BigDecimal distanceKm;
    public java.math.BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(java.math.BigDecimal value) { distanceKm = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne @JoinColumn(name="overseen_by_id")
    private StaffProfile overseenBy;
    public StaffProfile getOverseenBy() { return overseenBy; }
    public void setOverseenBy(StaffProfile value) { overseenBy = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "origin", length = 100, nullable = false)
    private String origin;

    @Column(name = "destination", length = 100, nullable = false)
    private String destination;

    @Column(name = "base_fare", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RouteStatus status = RouteStatus.ACTIVE;

    public enum RouteStatus {
        ACTIVE, INACTIVE
    }

    public Route() {
    }

    public Route(String origin, String destination, BigDecimal baseFare, RouteStatus status) {
        this.origin = origin;
        this.destination = destination;
        this.baseFare = baseFare;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
    }
}

