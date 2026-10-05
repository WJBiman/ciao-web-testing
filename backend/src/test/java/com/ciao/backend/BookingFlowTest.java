package com.ciao.backend;

import com.ciao.backend.dto.reservation.*;
import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RouteRepository routes;
    @Autowired ScheduleRepository schedules;
    @Autowired ReservationRepository reservations;
    @Autowired ReservedSeatRepository seats;
    @Autowired PaymentRepository payments;
    @Autowired BusRepository buses;
    @Autowired ReservationService booking;
    @Autowired PaymentService checkout;

    private Schedule schedule() {
        Route route = new Route();
        route.setOrigin("Test origin");
        route.setDestination("Test destination");
        route.setBaseFare(new BigDecimal("500.00"));
        route.setStatus(Route.RouteStatus.ACTIVE);
        routes.save(route);
        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setDepartureTime(LocalDateTime.now().plusDays(1));
        schedule.setArrivalTime(LocalDateTime.now().plusDays(1).plusHours(2));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        return schedules.save(schedule);
    }

    private ReservationRequest request(Schedule schedule, String... numbers) {
        ReservationRequest request = new ReservationRequest();
        request.setScheduleId(schedule.getId());
        request.setSeatNumbers(List.of(numbers));
        request.setPassengerName("Test passenger");
        request.setPassengerPhone("0771234567");
        return request;
    }

    private PaymentRequest payment(Integer id) {
        PaymentRequest request = new PaymentRequest();
        request.setReservationId(id);
        request.setCardNumber("4111111111111111"); request.setCardholderName("CIAO TEST"); request.setExpiry("12/30"); request.setCvv("123");
        return request;
    }

    @Test void onlyExactTestCardDetailsCanConfirmBooking() throws Exception {
        var reserved = booking.lockSeats(request(schedule(), "2"), null);
        int id = reserved.getId();
        PaymentRequest empty = json.readValue("{\"demoOutcome\":\"SUCCESS\"}", PaymentRequest.class);
        empty.setReservationId(id);
        assertEquals("FAILED", checkout.processCheckout(empty, null, id).getStatus());
        for (String field : List.of("name", "expiry", "cvv", "number")) {
            PaymentRequest invalid = payment(id);
            switch (field) {
                case "name" -> invalid.setCardholderName("OTHER NAME");
                case "expiry" -> invalid.setExpiry("01/31");
                case "cvv" -> invalid.setCvv("999");
                case "number" -> invalid.setCardNumber("5555555555554444");
            }
            assertEquals("FAILED", checkout.processCheckout(invalid, null, id).getStatus());
            assertEquals(Reservation.ReservationStatus.PENDING, reservations.findById(id).orElseThrow().getStatus());
        }
        PaymentRequest valid = payment(id);
        valid.setCardNumber("4111 1111 1111 1111");
        assertEquals("SUCCESS", checkout.processCheckout(valid, null, id).getStatus());
    }

    @Test void guestBookingOwnershipAndCheckout() throws Exception {
        MvcResult lock = mvc.perform(post("/api/reservations/lock").contentType("application/json")
                .content(json.writeValueAsString(request(schedule(), "1"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json.readTree(lock.getResponse().getContentAsString());
        int id = data.get("id").asInt();
        String token = data.get("guestToken").asText();
        mvc.perform(get("/api/reservations/ticket/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/ticket/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payments/checkout").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(payment(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(post("/api/payments/checkout").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(payment(id))))
                .andExpect(status().isBadRequest());
        assertEquals(ReservedSeat.SeatStatus.BOOKED, seats.findByReservationId(id).get(0).getStatus());
    }

    @Test void alternateSeatSpellingsAndDepartedSchedulesAreRejected() {
        Schedule schedule = schedule();
        for (String seat : List.of("01", " 1", "+1", "0", "50")) {
            assertThrows(RuntimeException.class, () -> booking.lockSeats(request(schedule, seat), null));
        }
        assertThrows(RuntimeException.class, () -> booking.lockSeats(request(schedule, "1", "1"), null));
        schedule.setDepartureTime(LocalDateTime.now().minusMinutes(1));
        schedules.save(schedule);
        assertThrows(RuntimeException.class, () -> booking.lockSeats(request(schedule, "1"), null));
    }

    @Test void expiredCheckoutCommitsCancellation() {
        ReservationResponse reserved = booking.lockSeats(request(schedule(), "2"), null);
        ReservedSeat seat = seats.findByReservationId(reserved.getId()).get(0);
        seat.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));
        seats.save(seat);
        assertEquals("FAILED", checkout.processCheckout(payment(reserved.getId()), null, reserved.getId()).getStatus());
        assertEquals(Reservation.ReservationStatus.CANCELLED, reservations.findById(reserved.getId()).orElseThrow().getStatus());
    }

    @Test void concurrentSeatRequestsAllowOnlyOneWinner() throws Exception {
        Schedule schedule = schedule();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            start.await();
            try { booking.lockSeats(request(schedule, "3"), null); return true; }
            catch (RuntimeException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(task), second = pool.submit(task);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentCheckoutsCreateOnlyOnePayment() throws Exception {
        ReservationResponse reserved = booking.lockSeats(request(schedule(), "4"), null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            start.await();
            try { return "SUCCESS".equals(checkout.processCheckout(payment(reserved.getId()), null, reserved.getId()).getStatus()); }
            catch (RuntimeException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(task), second = pool.submit(task);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, payments.findAll().stream().filter(p -> p.getReservation().getId().equals(reserved.getId())).count());
        } finally { pool.shutdownNow(); }
    }

    private Cookie registerAndLogin(String suffix) throws Exception {
        String email = suffix + "@example.test";
        String phone = "077" + String.format("%07d", Math.abs(suffix.hashCode()) % 10000000);
        String nic = String.format("%012d", Math.abs((long)suffix.hashCode()));
        String password = " test password ";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of(
                "fullName", "Verification user", "email", email, "phone", phone, "nic", nic, "password", password))))
                .andExpect(status().isOk());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_PASSENGER")).andReturn();
        Cookie cookie = login.getResponse().getCookie("ciao_jwt");
        assertNotNull(cookie);
        return cookie;
    }

    @Test void cookieLoginAndPassengerTicketIsolation() throws Exception {
        Cookie owner = registerAndLogin(UUID.randomUUID().toString());
        Cookie other = registerAndLogin(UUID.randomUUID().toString());
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isOk());
        MvcResult lock = mvc.perform(post("/api/reservations/lock").cookie(owner).contentType("application/json")
                .content(json.writeValueAsString(request(schedule(), "5")))).andExpect(status().isOk()).andReturn();
        int id = json.readTree(lock.getResponse().getContentAsString()).get("id").asInt();
        mvc.perform(get("/api/reservations/ticket/" + id).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/ticket/" + id).cookie(owner)).andExpect(status().isOk());
        mvc.perform(get("/api/fleet/drivers").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fleet/buses/active")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(owner)).andExpect(status().isOk())
                .andExpect(cookie().maxAge("ciao_jwt", 0));
    }

    @Test void emptyPaymentCannotConfirmReservation() throws Exception {
        mvc.perform(post("/api/payments/checkout").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void failedDemoCanRetryAndOnlySuccessBooksSeats() {
        ReservationResponse reserved = booking.lockSeats(request(schedule(), "6"), null);
        PaymentRequest attempt = payment(reserved.getId());
        attempt.setCardNumber("5555555555554444");
        assertEquals("FAILED", checkout.processCheckout(attempt, null, reserved.getId()).getStatus());
        assertEquals(Reservation.ReservationStatus.PENDING, reservations.findById(reserved.getId()).orElseThrow().getStatus());
        assertEquals(ReservedSeat.SeatStatus.LOCKED, seats.findByReservationId(reserved.getId()).get(0).getStatus());
        attempt.setCardNumber("4111111111111111");
        PaymentResponse response = checkout.processCheckout(attempt, null, reserved.getId());
        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getTransactionId().startsWith("DEMO-"));
        assertEquals(Reservation.ReservationStatus.CONFIRMED, reservations.findById(reserved.getId()).orElseThrow().getStatus());
    }

    @Test void cancelledDemoReleasesSeatsAndCannotBePaidAgain() {
        Schedule schedule = schedule();
        ReservationResponse reserved = booking.lockSeats(request(schedule, "7"), null);
        PaymentRequest attempt = payment(reserved.getId());
        attempt.setCancel(true);
        assertEquals("CANCELLED", checkout.processCheckout(attempt, null, reserved.getId()).getStatus());
        assertEquals(Reservation.ReservationStatus.CANCELLED, reservations.findById(reserved.getId()).orElseThrow().getStatus());
        assertFalse(seats.findAllUnavailableSeatsForSchedule(schedule.getId(), LocalDateTime.now()).contains("7"));
        assertNotNull(booking.lockSeats(request(schedule, "7"), null));
        assertThrows(RuntimeException.class, () -> checkout.processCheckout(payment(reserved.getId()), null, reserved.getId()));
    }

    @Test void publicSeatMapUsesAssignedBusCapacity() throws Exception {
        Schedule schedule = schedule();
        Bus bus = buses.save(new Bus("TEST-" + System.nanoTime() % 1000000, 32, "AC", Bus.BusStatus.ACTIVE));
        schedule.setBus(bus);
        schedules.save(schedule);
        booking.lockSeats(request(schedule, "32"), null);
        mvc.perform(get("/api/schedules/" + schedule.getId() + "/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(32))
                .andExpect(jsonPath("$.bookable").value(true))
                .andExpect(jsonPath("$.unavailableSeats[0]").value("32"));
        assertThrows(RuntimeException.class, () -> booking.lockSeats(request(schedule, "33"), null));
    }

    @Test void searchOmitsDepartedAndCompletedTrips() {
        Schedule future = schedule();
        Schedule departed = schedule();
        departed.setDepartureTime(LocalDateTime.now().minusHours(1));
        schedules.save(departed);
        Schedule completed = schedule();
        completed.setStatus(Schedule.ScheduleStatus.COMPLETED);
        schedules.save(completed);
        LocalDate travelDate = LocalDate.now().plusDays(1);
        List<Integer> ids = schedules.searchSchedules("Test origin", "Test destination", LocalDateTime.now(), travelDate.atStartOfDay(), travelDate.plusDays(1).atStartOfDay())
                .stream().map(Schedule::getId).toList();
        assertTrue(ids.contains(future.getId()));
        assertFalse(ids.contains(departed.getId()));
        assertFalse(ids.contains(completed.getId()));
    }

    @Test
    void testFullBookingFlowEffectiveFareConsistency() throws Exception {
        Route route = new Route("Colombo", "Kandy", new java.math.BigDecimal("1450.00"), Route.RouteStatus.ACTIVE);
        routes.save(route);
        Bus superBus = buses.save(new Bus("EXP-8899", 40, "Luxury AC", Bus.BusStatus.ACTIVE));
        superBus.setBusType("SUPER_LUXURY_EXPRESSWAY");
        buses.save(superBus);

        Schedule schedule = new Schedule(route, superBus, null, java.time.LocalDateTime.now().plusDays(2), java.time.LocalDateTime.now().plusDays(2).plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        schedules.save(schedule);

        // 1. Schedule search endpoint returns effectiveSeatFare of 3300.00 (1450*2 + 400)
        String searchDate = java.time.LocalDate.now().plusDays(2).toString();
        mvc.perform(get("/api/schedules/search?origin=Colombo&destination=Kandy&date=" + searchDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].effectiveSeatFare").value(3300.00));

        // 2. Seat-map endpoint returns effectiveSeatFare of 3300.00
        mvc.perform(get("/api/schedules/" + schedule.getId() + "/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveSeatFare").value(3300.00));

        // 3. Locking 2 seats computes totalFare of 6600.00 (2 * 3300.00)
        ReservationRequest lockReq = new ReservationRequest();
        lockReq.setScheduleId(schedule.getId());
        lockReq.setPassengerName("Test Passenger");
        lockReq.setPassengerPhone("0771234567");
        lockReq.setSeatNumbers(java.util.List.of("1", "2"));

        ReservationResponse lockResp = booking.lockSeats(lockReq, null);
        assertEquals(new java.math.BigDecimal("6600.00"), lockResp.getTotalFare());

        // 4. Checkout completes with exact total fare of 6600.00
        PaymentRequest payReq = payment(lockResp.getId());

        PaymentResponse payResp = checkout.processCheckout(payReq, null, lockResp.getId());
        assertEquals("SUCCESS", payResp.getStatus());

        Reservation confirmed = reservations.findById(lockResp.getId()).orElseThrow();
        assertEquals(Reservation.ReservationStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(new java.math.BigDecimal("6600.00"), confirmed.getTotalFare());
    }
}
