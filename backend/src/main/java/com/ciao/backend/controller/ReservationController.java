package com.ciao.backend.controller;

import com.ciao.backend.dto.reservation.ReservationRequest;
import com.ciao.backend.dto.reservation.ReservationResponse;
import com.ciao.backend.entity.Reservation;
import com.ciao.backend.security.JwtUtils;
import com.ciao.backend.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")

public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/schedules/{scheduleId}/seats")
    public ResponseEntity<?> getUnavailableSeats(@PathVariable Integer scheduleId) {
        try {
            List<String> seats = reservationService.getUnavailableSeats(scheduleId);
            return ResponseEntity.ok(seats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/schedules/{scheduleId}/seat-map")
    public ResponseEntity<?> getSeatMap(@PathVariable Integer scheduleId) {
        return ResponseEntity.ok(reservationService.getSeatMap(scheduleId));
    }

    @PostMapping("/reservations/lock")
    public ResponseEntity<?> lockSeats(@jakarta.validation.Valid @RequestBody ReservationRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = null;
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
            }

            ReservationResponse response = reservationService.lockSeats(request, username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/reservations/my")
    public ResponseEntity<?> getMyReservations() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401).body("{\"message\": \"Unauthorized: Please sign in to view your bookings.\"}");
            }

            String username = authentication.getName();
            List<com.ciao.backend.dto.reservation.UserReservationDTO> reservations = reservationService.getUserReservations(username);
            return ResponseEntity.ok(reservations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/reservations/ticket/{reservationId}")
    public ResponseEntity<?> getTicketDetails(@PathVariable Integer reservationId, HttpServletRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = null;
            boolean isStaffOrAdmin = false;
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
                isStaffOrAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
            }
            
            String authHeader = request.getHeader("Authorization");
            Integer guestResId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                guestResId = jwtUtils.getReservationIdFromGuestToken(authHeader.substring(7));
            }

            // Retrieve raw reservation to verify ownership
            Reservation reservation = reservationService.getRawReservation(reservationId);

            if (reservation.getUser() != null) {
                // Reservation belongs to a registered passenger
                if (!isStaffOrAdmin) {
                    if (username == null) {
                        return ResponseEntity.status(401).body("{\"message\": \"Unauthorized: Login required to view this ticket.\"}");
                    }
                    boolean owns = (reservation.getUser().getEmail() != null && reservation.getUser().getEmail().equals(username)) ||
                                   (reservation.getUser().getPhone() != null && reservation.getUser().getPhone().equals(username));
                    if (!owns) {
                        return ResponseEntity.status(403).body("{\"message\": \"Forbidden: This ticket belongs to another passenger.\"}");
                    }
                }
            } else {
                // Guest reservation
                if (!isStaffOrAdmin) {
                    if (guestResId == null || !guestResId.equals(reservationId)) {
                        return ResponseEntity.status(403).body("{\"message\": \"Forbidden: Invalid or missing guest reservation token.\"}");
                    }
                }
            }

            ReservationResponse response = reservationService.getTicketDetails(reservationId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    public record CancelRequestInput(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String reason) {}

    @PostMapping("/reservations/{id}/cancel-request")
    public ResponseEntity<?> requestCancellation(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody CancelRequestInput input) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401).body(java.util.Map.of("message", "Please sign in to continue."));
            }

            String username = authentication.getName();
            return ResponseEntity.ok(reservationService.requestCancellation(id, username, input.reason()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to process cancellation request."));
        }
    }
}

