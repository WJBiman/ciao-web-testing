package com.ciao.backend.dto;

public class PublicBranchResponse {
    private Integer id;
    private String location;
    private String contactNumber;

    public PublicBranchResponse() {}

    public PublicBranchResponse(Integer id, String location, String contactNumber) {
        this.id = id;
        this.location = location;
        this.contactNumber = contactNumber;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
}
