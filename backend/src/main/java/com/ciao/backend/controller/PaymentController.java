package com.ciao.backend.controller;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.dto.reservation.PaymentResponse;
import com.ciao.backend.security.JwtUtils;
import com.ciao.backend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")

public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/payments/checkout")
    public ResponseEntity<?> processCheckout(@jakarta.validation.Valid @RequestBody PaymentRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = null;
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
            }

            String authHeader = httpRequest.getHeader("Authorization");
            Integer guestResId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                guestResId = jwtUtils.getReservationIdFromGuestToken(authHeader.substring(7));
            }

            PaymentResponse response = paymentService.processCheckout(request, username, guestResId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new com.ciao.backend.dto.MessageResponse(e.getMessage()));
        }
    }
}

