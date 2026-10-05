package com.ciao.backend.controller;

import com.ciao.backend.dto.parcel.ParcelBookingRequest;
import com.ciao.backend.dto.parcel.ParcelResponse;
import com.ciao.backend.dto.parcel.ParcelStatusUpdateRequest;
import com.ciao.backend.dto.parcel.ParcelTrackingResponse;
import com.ciao.backend.service.ParcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {

    @Autowired
    private ParcelService parcelService;

    // Public endpoint: Submit a new parcel booking request
    @PostMapping(value = {"", "/"})
    public ResponseEntity<?> createParcel(@jakarta.validation.Valid @RequestBody ParcelBookingRequest request) {
        try {
            ParcelResponse response = parcelService.createParcel(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    // Public endpoint: Quote parcel delivery fee
    @GetMapping("/quote")
    public ResponseEntity<?> quoteParcel(@RequestParam java.math.BigDecimal weight) {
        try {
            java.util.Map<String, Object> quote = parcelService.quoteFee(weight);
            return ResponseEntity.ok(quote);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    // Public endpoint: Track parcel
    @GetMapping("/track/{trackingId}")
    public ResponseEntity<?> trackParcel(@PathVariable String trackingId) {
        try {
            ParcelTrackingResponse response = parcelService.trackParcel(trackingId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    // Admin/Staff endpoint: View all parcels
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getAllParcels() {
        try {
            List<ParcelResponse> responses = parcelService.getAllParcels();
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    // Admin/Staff endpoint: View specific parcel
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getParcelById(@PathVariable Integer id) {
        try {
            ParcelResponse response = parcelService.getParcelById(id);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }

    // Admin/Staff endpoint: Update parcel status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody ParcelStatusUpdateRequest request) {
        try {
            ParcelResponse response = parcelService.updateStatus(id, request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\": \"" + e.getMessage() + "\"}");
        }
    }
}
