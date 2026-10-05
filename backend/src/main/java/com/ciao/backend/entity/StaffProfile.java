package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="staff_profiles")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class StaffProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @OneToOne @JoinColumn(name="user_id", nullable=false, unique=true)
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    @Column(unique=true)
    private String employeeCode;
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String value) { employeeCode = value; }
    
    private java.time.LocalDate hireDate;
    public java.time.LocalDate getHireDate() { return hireDate; }
    public void setHireDate(java.time.LocalDate value) { hireDate = value; }
    @Column(nullable=false)
    private String staffType;
    public String getStaffType() { return staffType; }
    public void setStaffType(String value) { staffType = value; }
}

