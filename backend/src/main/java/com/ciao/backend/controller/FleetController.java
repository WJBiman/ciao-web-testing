package com.ciao.backend.controller;

import com.ciao.backend.dto.BusRequest;
import com.ciao.backend.dto.DriverRequest;
import com.ciao.backend.dto.MessageResponse;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Driver;
import com.ciao.backend.service.BusService;
import com.ciao.backend.service.DriverService;
import com.ciao.backend.service.HardDeleteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    @Autowired
    private BusService busService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private HardDeleteService hardDelete;

    // --- BUS ENDPOINTS ---

    @GetMapping("/buses")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<List<Bus>> getAllBuses() {
        return ResponseEntity.ok(busService.getAllBuses());
    }

    @Autowired
    private com.ciao.backend.repository.BranchRepository branchRepository;

    @GetMapping("/branches")
    public ResponseEntity<List<com.ciao.backend.dto.PublicBranchResponse>> getAllBranches() {
        List<com.ciao.backend.dto.PublicBranchResponse> publicBranches = branchRepository.findAll().stream()
                .map(b -> new com.ciao.backend.dto.PublicBranchResponse(b.getId(), b.getLocation(), b.getContactNumber()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(publicBranches);
    }

    @GetMapping("/buses/active")
    public ResponseEntity<List<Bus>> getActiveBuses() {
        return ResponseEntity.ok(busService.getActiveBuses());
    }

    @PostMapping("/buses")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> createBus(@jakarta.validation.Valid @RequestBody BusRequest request) {
        try {
            Bus bus = busService.createBus(request);
            return ResponseEntity.ok(bus);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PutMapping("/buses/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateBus(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody BusRequest request) {
        try {
            Bus bus = busService.updateBus(id, request);
            return ResponseEntity.ok(bus);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/buses/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> deleteBus(@PathVariable Integer id) {
        hardDelete.requireOperationsManager();
        hardDelete.delete("buses", id, null);
        return ResponseEntity.ok(new MessageResponse("Bus permanently deleted."));
    }

    // --- DRIVER ENDPOINTS ---

    @GetMapping("/drivers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<List<Driver>> getAllDrivers() {
        return ResponseEntity.ok(driverService.getAllDrivers());
    }

    @PostMapping("/drivers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> createDriver(@jakarta.validation.Valid @RequestBody DriverRequest request) {
        try {
            Driver driver = driverService.createDriver(request);
            return ResponseEntity.ok(driver);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PutMapping("/drivers/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateDriver(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody DriverRequest request) {
        try {
            Driver driver = driverService.updateDriver(id, request);
            return ResponseEntity.ok(driver);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/drivers/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> deleteDriver(@PathVariable Integer id) {
        hardDelete.requireOperationsManager();
        hardDelete.delete("drivers", id, null);
        return ResponseEntity.ok(new MessageResponse("Driver permanently deleted."));
    }
}
