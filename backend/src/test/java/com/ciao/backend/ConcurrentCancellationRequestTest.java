package com.ciao.backend;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.security.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentCancellationRequestTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RouteRepository routes;
    @Autowired BusRepository buses;
    @Autowired ScheduleRepository schedules;
    @Autowired ReservationRepository reservations;
    @Autowired CancellationRequestRepository cancellationRequests;
    @Autowired JwtUtils jwtUtils;

    @Test
    void simultaneousRequestsCreateExactlyOnePendingCancellation() throws Exception {
        Role role = roles.findByRoleName("PASSENGER").orElseGet(() -> {
            Role created = new Role();
            created.setRoleName("PASSENGER");
            return roles.save(created);
        });
        User passenger = new User();
        passenger.setFullName("Concurrent Passenger");
        passenger.setEmail("concurrent-" + UUID.randomUUID() + "@ciao.test");
        passenger.setUsername("concurrent-" + UUID.randomUUID());
        passenger.setPhone("0771234567");
        passenger.setPasswordHash("test-only-hash");
        passenger.setRole(role);
        passenger = users.save(passenger);

        Route route = routes.save(new Route("Colombo", "Kandy", new BigDecimal("1450.00"), Route.RouteStatus.ACTIVE));
        Bus bus = buses.save(new Bus("CC-" + UUID.randomUUID().toString().substring(0, 6), 49, "AC", Bus.BusStatus.ACTIVE));
        Schedule schedule = schedules.save(new Schedule(route, bus, null, LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        Reservation reservation = reservations.save(new Reservation(schedule, passenger, passenger.getFullName(),
                passenger.getPhone(), new BigDecimal("3300.00"), Reservation.ReservationStatus.CONFIRMED));

        String token = jwtUtils.generateTokenFromUsername(passenger.getEmail());
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                start.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/eer/reservations/" + reservation.getId() + "/cancel-request")
                                .cookie(new Cookie("ciao_jwt", token))
                                .contentType("application/json")
                                .content("{\"reason\":\"Concurrent cancellation\"}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();
            int firstStatus = first.get(20, TimeUnit.SECONDS);
            int secondStatus = second.get(20, TimeUnit.SECONDS);
            assertTrue((firstStatus == 200 && secondStatus == 409)
                    || (firstStatus == 409 && secondStatus == 200),
                    "Expected one accepted request and one conflict, got " + firstStatus + " and " + secondStatus);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, cancellationRequests.findByReservationId(reservation.getId()).size());
    }
}
