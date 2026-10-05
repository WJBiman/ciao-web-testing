package com.ciao.backend.controller;

import com.ciao.backend.dto.LostItemRequest;
import com.ciao.backend.dto.LostItemResponse;
import com.ciao.backend.dto.LostItemStatusUpdateRequest;
import com.ciao.backend.entity.LostItemStatus;
import com.ciao.backend.service.LostItemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {

    @Autowired
    private LostItemService lostItemService;

    /**
     * PUBLIC — any passenger/person can report a lost item.
     */
    @PostMapping
    public ResponseEntity<LostItemResponse> reportLostItem(@Valid @RequestBody LostItemRequest request) {
        LostItemResponse response = lostItemService.reportLostItem(request);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * ADMIN/STAFF only — list all lost items with optional search/filter.
     * Does NOT expose a public listing endpoint.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<LostItemResponse>> getAllItems(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LostItemStatus status) {
        List<LostItemResponse> items = lostItemService.getAllItems(search, status);
        return ResponseEntity.ok(items);
    }

    /**
     * ADMIN/STAFF only — update a lost item status (LOST→FOUND→CLAIMED).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<LostItemResponse> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody LostItemStatusUpdateRequest request) {
        LostItemResponse updated = lostItemService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(updated);
    }
}
