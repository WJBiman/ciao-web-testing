package com.ciao.backend.dto;

import java.util.List;

public class UserInfoResponse {
    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    private List<String> roles;

    public UserInfoResponse() {}

    public UserInfoResponse(Integer id, String fullName, String email, String phone, List<String> roles) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.roles = roles;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
