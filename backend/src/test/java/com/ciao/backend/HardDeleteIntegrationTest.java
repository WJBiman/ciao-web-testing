package com.ciao.backend;

import com.ciao.backend.service.HardDeleteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.ciao.backend.security.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HardDeleteIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HardDeleteService delete;
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;

    private int insert(String sql, Object... values) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().intValue();
    }

    private void deleted(String module, String table, int id) {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id));
        delete.delete(module, id, -1);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id), module);
    }

    private int route() {
        return insert("INSERT INTO routes (origin, destination, base_fare, status) VALUES (?, ?, ?, ?)", "DeleteTestOrigin", "DeleteTestDestination", 200, "ACTIVE");
    }

    private int bus() {
        return insert("INSERT INTO buses (plate_number, capacity, status) VALUES (?, ?, ?)", "DT-" + String.valueOf(System.nanoTime()).substring(0, 10), 2, "ACTIVE");
    }

    private int user() {
        int role = insert("INSERT INTO roles (role_name) VALUES (?)", "DELETE_TEST_" + System.nanoTime());
        return insert("INSERT INTO users (role_id, full_name, username, email, phone, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
                role, "Delete Test", "delete_" + System.nanoTime(), "delete" + System.nanoTime() + "@test.local", "07" + String.valueOf(System.nanoTime()).substring(0, 8), "test-only");
    }

    @Test
    void createsAndPhysicallyDeletesOperationalRecords() {
        int branch = insert("INSERT INTO branches (location, contact_number) VALUES (?, ?)", "Delete Test Branch", "0110000000");
        deleted("branches", "branches", branch);

        int driver = insert("INSERT INTO drivers (driver_name, license_number, contact_number, status) VALUES (?, ?, ?, ?)",
                "Delete Test Driver", "DL-" + System.nanoTime(), "0770000000", "AVAILABLE");
        deleted("drivers", "drivers", driver);

        int route = route();
        int stop = insert("INSERT INTO route_stops (route_id, sequence_number, location_name) VALUES (?, ?, ?)", route, 1, "Delete Test Stop");
        deleted("routes", "routes", route);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM route_stops WHERE id = ?", Integer.class, stop));

        int bus = bus();
        int seat = insert("INSERT INTO bus_seats (bus_id, seat_number, seat_status) VALUES (?, ?, ?)", bus, "1", "AVAILABLE");
        deleted("buses", "buses", bus);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bus_seats WHERE id = ?", Integer.class, seat));

        int scheduleRoute = route();
        int schedule = insert("INSERT INTO schedules (route_id, departure_time, arrival_time, status, repeat_daily, is_charter) VALUES (?, ?, ?, ?, ?, ?)",
                scheduleRoute, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(3), "SCHEDULED", false, false);
        deleted("schedules", "schedules", schedule);
        deleted("routes", "routes", scheduleRoute);
    }

    @Test
    void createsAndPhysicallyDeletesServiceRecords() {
        int bus = bus();
        int parcel = insert("INSERT INTO parcels (bus_id, tracking_id, sender_name, sender_phone, receiver_name, receiver_phone, weight, total_fee, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bus, "DELETE-PAR-" + System.nanoTime(), "Sender", "0771111111", "Receiver", "0772222222", 1, 100, "PENDING");
        deleted("parcels", "parcels", parcel);
        deleted("buses", "buses", bus);

        int lost = insert("INSERT INTO lost_items (item_description, reported_by_name, reported_by_phone, status) VALUES (?, ?, ?, ?)",
                "Delete Test Umbrella", "Reporter", "0773333333", "FOUND");
        int claimant = user();
        int claim = insert("INSERT INTO lost_item_claims (item_id, claimant_id, proof_of_ownership, claim_status) VALUES (?, ?, ?, ?)",
                lost, claimant, "Delete test", "PENDING");
        deleted("claims", "lost_item_claims", claim);
        deleted("lost-items", "lost_items", lost);

        int groupRoute = route();
        int groupSchedule = insert("INSERT INTO schedules (route_id, departure_time, arrival_time, status, repeat_daily, is_charter) VALUES (?, ?, ?, ?, ?, ?)",
                groupRoute, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2), "SCHEDULED", false, true);
        int groupBooking = insert("INSERT INTO bookings (schedule_id, booking_type, booking_status, total_fare) VALUES (?, ?, ?, ?)",
                groupSchedule, "GROUP", "PENDING", 5000);
        int group = insert("INSERT INTO group_bookings (booking_id, customer_name, customer_phone, start_date, end_date, passenger_count, total_cost, deposit_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                groupBooking, "Delete Test Group", "0774444444", LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), 5, 5000, 0, "PENDING_REVIEW");
        deleted("groups", "group_bookings", group);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class, groupBooking));
        assertFalse(jdbc.queryForObject("SELECT is_charter FROM schedules WHERE id = ?", Boolean.class, groupSchedule));
        deleted("schedules", "schedules", groupSchedule);
        deleted("routes", "routes", groupRoute);

        int payment = insert("INSERT INTO payments (amount, status) VALUES (?, ?)", 100, "FAILED");
        deleted("payments", "payments", payment);
    }

    @Test
    void deletesOnlyUnlinkedStaffAndPendingReservationWorkflow() {
        int staffUser = user();
        int staff = insert("INSERT INTO staff_profiles (user_id, employee_code, staff_type) VALUES (?, ?, ?)",
                staffUser, "DELETE-STAFF-" + System.nanoTime(), "OPERATIONS_MANAGER");
        deleted("staff", "staff_profiles", staff);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, staffUser));

        int route = route();
        int schedule = insert("INSERT INTO schedules (route_id, departure_time, arrival_time, status, repeat_daily, is_charter) VALUES (?, ?, ?, ?, ?, ?)",
                route, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(3), "SCHEDULED", false, false);
        int booking = insert("INSERT INTO bookings (schedule_id, booking_type, booking_status, total_fare) VALUES (?, ?, ?, ?)",
                schedule, "INDIVIDUAL", "PENDING", 200);
        int passenger = user();
        int reservation = insert("INSERT INTO reservations (booking_id, schedule_id, user_id, passenger_name, passenger_phone, total_fare, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                booking, schedule, passenger, "Delete Test Passenger", "0775555555", 200, "PENDING");
        int cancellation = insert("INSERT INTO cancellation_requests (reservation_id, requested_by_id, reason, status) VALUES (?, ?, ?, ?)",
                reservation, passenger, "Delete test cancellation", "PENDING");
        deleted("cancellations", "cancellation_requests", cancellation);
        deleted("reservations", "reservations", reservation);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class, booking));
        deleted("schedules", "schedules", schedule);
        deleted("routes", "routes", route);
    }

    @Test
    void preservesLinkedJourneysAndFinancialHistory() {
        int route = route();
        int schedule = insert("INSERT INTO schedules (route_id, departure_time, arrival_time, status, repeat_daily, is_charter) VALUES (?, ?, ?, ?, ?, ?)",
                route, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(3), "SCHEDULED", false, false);
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class, () -> delete.delete("routes", route, -1));
        assertEquals(409, blocked.getStatusCode().value());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM routes WHERE id = ?", Integer.class, route));
        deleted("schedules", "schedules", schedule);
        deleted("routes", "routes", route);

        int payment = insert("INSERT INTO payments (amount, status) VALUES (?, ?)", 100, "SUCCESS");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> delete.delete("payments", payment, -1)).getStatusCode().value());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE id = ?", Integer.class, payment));
    }

    @Test
    void onlyOperationsStaffMayUseDirectDeleteRoute() throws Exception {
        int role = jdbc.queryForObject("SELECT id FROM roles WHERE role_name = ?", Integer.class, "STAFF");
        int branchUser = insert("INSERT INTO users (role_id, full_name, username, email, phone, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
                role, "Branch Test", "branch_delete_test", "branch-delete@test.local", "0710000001", "test-only");
        insert("INSERT INTO staff_profiles (user_id, staff_type) VALUES (?, ?)", branchUser, "BRANCH_MANAGER");
        int operationsUser = insert("INSERT INTO users (role_id, full_name, username, email, phone, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
                role, "Operations Test", "operations_delete_test", "operations-delete@test.local", "0710000002", "test-only");
        insert("INSERT INTO staff_profiles (user_id, staff_type) VALUES (?, ?)", operationsUser, "OPERATIONS_MANAGER");
        int route = route();
        mvc.perform(delete("/api/routes/{id}", route).cookie(new Cookie("ciao_jwt", jwt.generateTokenFromUsername("branch-delete@test.local"))))
                .andExpect(status().isForbidden());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM routes WHERE id = ?", Integer.class, route));
        mvc.perform(delete("/api/routes/{id}", route).cookie(new Cookie("ciao_jwt", jwt.generateTokenFromUsername("operations-delete@test.local"))))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM routes WHERE id = ?", Integer.class, route));
    }
}
