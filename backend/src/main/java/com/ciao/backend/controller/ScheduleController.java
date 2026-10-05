package com.ciao.backend.controller;

import com.ciao.backend.dto.ScheduleRequest;
import com.ciao.backend.entity.Schedule;
import com.ciao.backend.service.ScheduleService;
import com.ciao.backend.service.HardDeleteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private HardDeleteService hardDelete;

    @GetMapping
    public ResponseEntity<List<Schedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleService.getAllSchedules());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Schedule>> searchSchedules(@RequestParam(defaultValue = "") String origin,
                                                          @RequestParam(defaultValue = "") String destination,
                                                          @RequestParam(required = false) java.time.LocalDate date) {
        return ResponseEntity.ok(scheduleService.searchSchedules(origin, destination,
                date != null ? date : java.time.LocalDate.now()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Schedule> createSchedule(@jakarta.validation.Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.createSchedule(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Schedule> updateSchedule(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.updateSchedule(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<?> deleteSchedule(@PathVariable Integer id) {
        hardDelete.requireOperationsManager();
        hardDelete.delete("schedules", id, null);
        return ResponseEntity.ok(java.util.Map.of("deleted", true, "id", id));
    }
}

