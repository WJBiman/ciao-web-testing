package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="user_phones", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","phone_number"}))
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class UserPhone {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="user_id", nullable=false)
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    @Column(nullable=false)
    private String phoneNumber;
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String value) { phoneNumber = value; }
}

