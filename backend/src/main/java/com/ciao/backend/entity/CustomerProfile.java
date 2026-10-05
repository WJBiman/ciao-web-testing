package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="customer_profiles")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class CustomerProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @OneToOne @JoinColumn(name="user_id", nullable=false, unique=true)
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    
    private String firstName;
    public String getFirstName() { return firstName; }
    public void setFirstName(String value) { firstName = value; }
    
    private String lastName;
    public String getLastName() { return lastName; }
    public void setLastName(String value) { lastName = value; }
    
    private String address;
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    
    private java.time.LocalDateTime registeredDate;
    public java.time.LocalDateTime getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(java.time.LocalDateTime value) { registeredDate = value; }
}

