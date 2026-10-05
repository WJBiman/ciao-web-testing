package com.ciao.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(?:\\+94|0)?7\\d{8}$", message = "Please enter a valid Sri Lankan mobile number (e.g. 07XXXXXXXX)")
    @Size(max = 15, message = "Phone number cannot exceed 15 characters")
    private String phone;

    @NotBlank(message = "NIC is required")
    @Pattern(regexp = "^(?:[0-9]{9}[vVxX]|[0-9]{12})$", message = "Please enter a valid Sri Lankan NIC (9 digits with V/X or 12 digits)")
    @Size(max = 15, message = "NIC cannot exceed 15 characters")
    private String nic;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must contain 6 to 72 characters")
    private String password;

    public RegisterRequest() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName == null ? null : fullName.trim(); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = com.ciao.backend.security.AccountIdentifiers.normalize(phone); }

    public String getNic() { return nic; }
    public void setNic(String nic) { this.nic = nic == null ? null : nic.trim(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
