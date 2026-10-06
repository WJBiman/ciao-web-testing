package com.ciao.backend.controller;

import com.ciao.backend.dto.groupbooking.GroupBookingRequest;
import com.ciao.backend.dto.groupbooking.GroupBookingResponse;
import com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest;
import com.ciao.backend.service.GroupBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group-bookings")
public class GroupBookingController {

    @Autowired
    private GroupBookingService groupBookingService;

    // Public endpoint: Submit a new group booking request
    @PostMapping
    public ResponseEntity<?> createBooking(@jakarta.validation.Valid @RequestBody GroupBookingRequest request) {
        try {
            GroupBookingResponse response = groupBookingService.createBookingRequest(request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to create booking"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getCustomerStatus(@RequestParam String reference, @RequestParam String phone) {
        try {
            return ResponseEntity.ok(groupBookingService.getCustomerStatus(reference, phone));
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Booking not found"));
        }
    }

    public record TokenRecoveryRequest(String reference, String phone, String name) {}
    @PostMapping("/recover-token")
    public ResponseEntity<?> recoverToken(@RequestBody TokenRecoveryRequest req) {
        try {
            String token = groupBookingService.recoverGuestAccessToken(req.reference(), req.phone(), req.name());
            return ResponseEntity.ok(java.util.Map.of("guestAccessToken", token, "message", "Guest access token recovered securely."));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to recover token"));
        }
    }

    public record CustomerCancelRequest(
            @jakarta.validation.constraints.NotBlank(message = "Booking reference is required") String reference,
            @jakarta.validation.constraints.NotBlank(message = "Customer phone is required") String phone,
            String reason
    ) {}

    @PostMapping("/cancel")
    public ResponseEntity<?> customerCancel(@jakarta.validation.Valid @RequestBody CustomerCancelRequest req) {
        try {
            GroupBookingResponse response = groupBookingService.cancelByCustomer(req.reference(), req.phone(), req.reason());
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to cancel group booking"));
        }
    }

    // Admin/Staff endpoint: View all booking requests
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getAllBookings() {
        try {
            List<GroupBookingResponse> responses = groupBookingService.getAllGroupBookings();
            return ResponseEntity.ok(responses);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to fetch bookings"));
        }
    }

    // Admin/Staff endpoint: View specific booking
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getBookingById(@PathVariable Integer id) {
        try {
            GroupBookingResponse response = groupBookingService.getGroupBookingById(id);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Booking not found"));
        }
    }

    // Admin/Staff endpoint: Update status and optionally assign a bus
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody GroupBookingStatusUpdateRequest request) {
        try {
            GroupBookingResponse response = groupBookingService.updateBookingStatus(id, request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to update status"));
        }
    }

    // Admin/Staff endpoint: Get only available, conflict-free buses for this booking
    @GetMapping("/{id}/available-buses")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getAvailableBuses(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(groupBookingService.getAvailableBusesForBooking(id));
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(java.util.Map.of("message", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to fetch available buses"));
        }
    }
}
