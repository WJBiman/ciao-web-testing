package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="route_stops", uniqueConstraints=@UniqueConstraint(columnNames={"route_id","sequence_number"}))
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class RouteStop {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="route_id", nullable=false)
    private Route route;
    public Route getRoute() { return route; }
    public void setRoute(Route value) { route = value; }
    @Column(nullable=false)
    private Integer sequenceNumber;
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer value) { sequenceNumber = value; }
    @Column(nullable=false)
    private String locationName;
    public String getLocationName() { return locationName; }
    public void setLocationName(String value) { locationName = value; }
}

