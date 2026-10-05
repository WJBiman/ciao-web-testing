package com.ciao.backend.security;

import com.ciao.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String email;
    private String phone;
    @JsonIgnore
    private String password;
    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Integer id, String email, String phone, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(User user) {
        String roleName = user.getRole() == null ? "PASSENGER" : user.getRole().getRoleName();
        roleName = roleName.trim().toUpperCase(java.util.Locale.ROOT);
        if (roleName.startsWith("ROLE_")) {
            roleName = roleName.substring("ROLE_".length());
        }
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
        );

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getPasswordHash(),
                authorities);
    }

    public Integer getId() {
        return id;
    }
    
    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    // Use email as the primary username. If email is null, use phone.
    @Override
    public String getUsername() {
        return email != null && !email.isBlank() ? email : phone;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }
}
