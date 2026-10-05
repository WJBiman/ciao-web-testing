package com.ciao.backend.service;

import com.ciao.backend.dto.LoginRequest;
import com.ciao.backend.dto.MessageResponse;
import com.ciao.backend.dto.RegisterRequest;
import com.ciao.backend.dto.UserInfoResponse;
import com.ciao.backend.entity.Role;
import com.ciao.backend.entity.User;
import com.ciao.backend.repository.RoleRepository;
import com.ciao.backend.repository.UserRepository;
import com.ciao.backend.security.JwtUtils;
import com.ciao.backend.security.UserDetailsImpl;
import com.ciao.backend.security.AccountIdentifiers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {
    @Autowired private EerService eer;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    public ResponseEntity<?> authenticateUser(LoginRequest loginRequest) {
        if (loginRequest == null || loginRequest.getUsername() == null || loginRequest.getPassword() == null
                || loginRequest.getUsername().trim().isEmpty() || loginRequest.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username and password are required."));
        }
        String identifier = loginRequest.getUsername().trim();
        if (identifier.contains("@")) {
            identifier = identifier.toLowerCase();
        }

        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, loginRequest.getPassword()));
        } catch (org.springframework.security.authentication.InternalAuthenticationServiceException ex) {
            return ResponseEntity.status(503).body(new MessageResponse("Sign-in service is temporarily unavailable. Please try again shortly."));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(new MessageResponse("Invalid username or password."));
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Get the real user to fetch fullName, as it's not in UserDetailsImpl (or we can add it to UserDetailsImpl)
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new UserInfoResponse(
                        userDetails.getId(),
                        user.getFullName(),
                        userDetails.getEmail(),
                        userDetails.getPhone(),
                        roles));
    }

    public ResponseEntity<?> registerUser(RegisterRequest signUpRequest) {
        String fullName = signUpRequest.getFullName() != null ? signUpRequest.getFullName().trim() : "";
        String email = signUpRequest.getEmail() != null ? signUpRequest.getEmail().trim().toLowerCase() : "";
        String phone = signUpRequest.getPhone() != null ? signUpRequest.getPhone().trim() : "";
        String nic = signUpRequest.getNic() != null ? signUpRequest.getNic().trim().toUpperCase() : "";

        if (!email.isEmpty() && userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        phone = AccountIdentifiers.normalize(phone);
        if (signUpRequest.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest().body(new MessageResponse("Password must not exceed 72 UTF-8 bytes."));
        }
        if (userRepository.existsByPhoneIn(AccountIdentifiers.phoneForms(phone))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Phone number is already in use!"));
        }

        if (userRepository.existsByNic(nic)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: NIC number is already in use!"));
        }

        // Create new user's account
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setNic(nic);
        user.setPasswordHash(encoder.encode(signUpRequest.getPassword()));

        // Default role is PASSENGER
        Role passengerRole = roleRepository.findByRoleName("PASSENGER")
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        user.setRole(passengerRole);

        userRepository.save(user);
        eer.profile(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
    
    public ResponseEntity<?> logoutUser() {
        ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("You've been signed out!"));
    }

    public ResponseEntity<?> getCurrentUser(String username) {
        if (username == null || username.isEmpty()) {
            return ResponseEntity.status(401).body(new MessageResponse("Error: User is not authenticated."));
        }

        String identifier = username.trim();
        User user = userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> userRepository.findByPhone(identifier).orElse(null));

        if (user == null) {
            return ResponseEntity.status(404).body(new MessageResponse("Error: User not found."));
        }

        List<String> roles = UserDetailsImpl.build(user).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        return ResponseEntity.ok(new UserInfoResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                roles));
    }
}
