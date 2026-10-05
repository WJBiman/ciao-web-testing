package com.ciao.backend.controller;

import com.ciao.backend.dto.RouteRequest;
import com.ciao.backend.entity.Route;
import com.ciao.backend.service.RouteService;
import com.ciao.backend.service.HardDeleteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @Autowired
    private HardDeleteService hardDelete;

    @GetMapping
    public ResponseEntity<List<Route>> getAllRoutes() {
        return ResponseEntity.ok(routeService.getAllRoutes());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Route> createRoute(@jakarta.validation.Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routeService.createRoute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Route> updateRoute(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routeService.updateRoute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<?> deleteRoute(@PathVariable Integer id) {
        hardDelete.requireOperationsManager();
        routeService.deleteRouteWithDependencies(id);
        return ResponseEntity.ok(java.util.Map.of("deleted", true, "id", id));
    }
}


