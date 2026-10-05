package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="branches")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Branch {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @Column(nullable=false)
    private String location;
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    
    private String contactNumber;
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String value) { contactNumber = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne @JoinColumn(name="manager_id", unique=true)
    private StaffProfile manager;
    public StaffProfile getManager() { return manager; }
    public void setManager(StaffProfile value) { manager = value; }
}

