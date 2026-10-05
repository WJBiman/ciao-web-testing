package com.ciao.backend;

import com.ciao.backend.controller.EerController;
import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CancellationAndRefundLifecycleTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private BusRepository buses;
    @Autowired private RouteRepository routes;
    @Autowired private ScheduleRepository schedules;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservedSeatRepository reservedSeats;
    @Autowired private TicketRepository tickets;
    @Autowired private StaffProfileRepository staff;
    @Autowired private PaymentRepository payments;
    @Autowired private CancellationRequestRepository cancellationRequests;
    @Autowired private JwtUtils jwtUtils;

    private User passenger;
    private User otherPassenger;
    private StaffProfile supervisor;
    private StaffProfile financeManager;
    private Schedule activeSchedule;
    private Schedule departedSchedule;

    private Cookie userCookie(User u) {
        String token = jwtUtils.generateTokenFromUsername(u.getEmail());
        Cookie c = new Cookie("ciao_jwt", token);
        c.setPath("/api");
        return c;
    }

    private User createAccount(String fullName, String email, String phone, String roleName) {
        Role r = roles.findByRoleName(roleName).orElseGet(() -> {
            Role newR = new Role();
            newR.setRoleName(roleName);
            return roles.save(newR);
        });
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhone(phone);
        u.setUsername("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        u.setPasswordHash(encoder.encode("password123"));
        u.setRole(r);
        return users.save(u);
    }

    @BeforeEach
    void setup() {
        passenger = createAccount("Kamal Passenger", "kamal@ciao.test", "0771234567", "PASSENGER");
        otherPassenger = createAccount("Nimal Passenger", "nimal@ciao.test", "0777654321", "PASSENGER");

        User supUser = createAccount("Sanduni Supervisor", "sanduni.sup@ciao.test", "0773344556", "STAFF");
        supervisor = new StaffProfile();
        supervisor.setUser(supUser);
        supervisor.setStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        supervisor.setEmployeeCode("EMP-SUP-1");
        supervisor.setHireDate(java.time.LocalDate.now());
        supervisor = staff.save(supervisor);

        User finUser = createAccount("Kasun Finance", "kasun.fin@ciao.test", "0775566778", "STAFF");
        financeManager = new StaffProfile();
        financeManager.setUser(finUser);
        financeManager.setStaffType("FINANCE_MANAGER");
        financeManager.setEmployeeCode("EMP-FIN-1");
        financeManager.setHireDate(java.time.LocalDate.now());
        financeManager = staff.save(financeManager);

        Route route = new Route("Colombo", "Kandy", new BigDecimal("1450.00"), Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = new Bus("NC-2200", 49, "AC", Bus.BusStatus.ACTIVE);
        bus.setBusType("SUPER_LUXURY_EXPRESSWAY");
        bus = buses.save(bus);

        // Active upcoming schedule (departing in 2 days)
        activeSchedule = new Schedule(route, bus, null, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        activeSchedule = schedules.save(activeSchedule);

        // Departed schedule (departed 2 hours ago)
        departedSchedule = new Schedule(route, bus, null, LocalDateTime.now().minusHours(2), LocalDateTime.now().plusHours(1), Schedule.ScheduleStatus.IN_TRANSIT);
        departedSchedule = schedules.save(departedSchedule);
    }

    private Reservation createConfirmedReservation(User user, Schedule schedule, BigDecimal totalFare, String... seats) {
        Reservation r = new Reservation(schedule, user, user.getFullName(), user.getPhone(), totalFare, Reservation.ReservationStatus.CONFIRMED);
        r = reservations.save(r);

        for (String seat : seats) {
            ReservedSeat rs = new ReservedSeat(r, seat, LocalDateTime.now().plusDays(5), ReservedSeat.SeatStatus.BOOKED);
            reservedSeats.save(rs);
        }

        Payment payment = new Payment(r, totalFare, Payment.PaymentMethod.CARD, Payment.PaymentStatus.SUCCESS, "TX-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setPaymentType("TICKET");
        payments.save(payment);

        return r;
    }

    @Test
    @DisplayName("Positive Flow: Passenger requests cancellation, supervisor approves, seats released and simulated refund recorded")
    void testPositiveCancellationAndSimulatedRefund() throws Exception {
        var beforeReport = json.readTree(mvc.perform(get("/api/eer/financial-report")
                .cookie(userCookie(financeManager.getUser()))).andReturn().getResponse().getContentAsString());
        Reservation r = createConfirmedReservation(passenger, activeSchedule, new BigDecimal("3300.00"), "12");

        // 1. Passenger submits cancellation request
        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Family medical emergency"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reservationId").value(r.getId()));

        // Verify request persisted in DB
        CancellationRequest cr = cancellationRequests.findPendingByReservationId(r.getId()).orElseThrow();
        assertEquals("Family medical emergency", cr.getReason());
        assertEquals(CancellationRequest.RequestStatus.PENDING, cr.getStatus());
        assertEquals(passenger.getId(), cr.getRequestedBy().getId());

        // 2. Supervisor approves cancellation
        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/adjudicate-cancellation?approve=true&notes=Approved per emergency policy")
                        .cookie(userCookie(supervisor.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.simulatedRefundAmount").value(3300.00));

        // 3. Verify reservation cancelled
        Reservation updatedRes = reservations.findById(r.getId()).orElseThrow();
        assertEquals(Reservation.ReservationStatus.CANCELLED, updatedRes.getStatus());

        // 4. Verify seats released (lock expiration set to now)
        List<ReservedSeat> seats = reservedSeats.findByReservationId(r.getId());
        assertFalse(seats.isEmpty());
        for (ReservedSeat s : seats) {
            assertTrue(s.getLockExpiresAt().isBefore(LocalDateTime.now().plusSeconds(2)));
        }

        // 5. Verify payment recorded as REFUNDED (simulated refund)
        List<Payment> paymentList = payments.findByReservationId(r.getId());
        assertTrue(paymentList.stream().anyMatch(p -> p.getStatus() == Payment.PaymentStatus.REFUNDED));

        // 6. Verify financial report reflects refund deduction
        var afterReport = json.readTree(mvc.perform(get("/api/eer/financial-report")
                .cookie(userCookie(financeManager.getUser()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, afterReport.path("refundedTransactions").asInt() - beforeReport.path("refundedTransactions").asInt());
        assertEquals(0, afterReport.path("grossCollected").decimalValue()
                .subtract(beforeReport.path("grossCollected").decimalValue())
                .compareTo(new BigDecimal("3300.00")));
        assertEquals(0, afterReport.path("totalRefunded").decimalValue()
                .subtract(beforeReport.path("totalRefunded").decimalValue())
                .compareTo(new BigDecimal("3300.00")));
        assertEquals(0, afterReport.path("ticketRevenue").decimalValue()
                .compareTo(beforeReport.path("ticketRevenue").decimalValue()));
        assertEquals(0, afterReport.path("totalRevenue").decimalValue()
                .compareTo(beforeReport.path("totalRevenue").decimalValue()));
    }

    @Test
    @DisplayName("Negative Flow: Rejection retains booking, records notes, and does not refund")
    void testRejectionRetainsBooking() throws Exception {
        Reservation r = createConfirmedReservation(passenger, activeSchedule, new BigDecimal("3300.00"), "14");

        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Changed my mind"))))
                .andExpect(status().isOk());

        // Supervisor rejects
        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/adjudicate-cancellation?approve=false&notes=Non-refundable fare class")
                        .cookie(userCookie(supervisor.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Reservation retained = reservations.findById(r.getId()).orElseThrow();
        assertEquals(Reservation.ReservationStatus.CONFIRMED, retained.getStatus());

        CancellationRequest cr = cancellationRequests.findByReservationId(r.getId()).get(0);
        assertEquals(CancellationRequest.RequestStatus.REJECTED, cr.getStatus());
        assertEquals("Non-refundable fare class", cr.getAdjudicationNotes());

        // Payment remains SUCCESS, not REFUNDED
        Payment p = payments.findByReservationId(r.getId()).get(0);
        assertEquals(Payment.PaymentStatus.SUCCESS, p.getStatus());
    }

    @Test
    @DisplayName("Negative Flow: Non-owner cannot request cancellation")
    void testNonOwnerCannotRequestCancellation() throws Exception {
        Reservation r = createConfirmedReservation(passenger, activeSchedule, new BigDecimal("3300.00"), "15");

        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/cancel-request")
                        .cookie(userCookie(otherPassenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Malicious attempt"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A guest reservation ID alone never grants a logged-in passenger cancellation access")
    void testGuestReservationRequiresVerifiedOwnership() throws Exception {
        Reservation guest = reservations.save(new Reservation(activeSchedule, null, "Guest Passenger", "0770000099",
                new BigDecimal("3300.00"), Reservation.ReservationStatus.CONFIRMED));

        mvc.perform(post("/api/eer/reservations/" + guest.getId() + "/cancel-request")
                        .cookie(userCookie(otherPassenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Claiming someone else's guest booking"))))
                .andExpect(status().isForbidden());
        assertTrue(cancellationRequests.findByReservationId(guest.getId()).isEmpty());
    }

    @Test
    @DisplayName("Cancelling an unpaid reservation does not fabricate a refund")
    void testUnpaidCancellationHasNoRefund() throws Exception {
        var beforeReport = json.readTree(mvc.perform(get("/api/eer/financial-report")
                .cookie(userCookie(financeManager.getUser()))).andReturn().getResponse().getContentAsString());
        Reservation unpaid = reservations.save(new Reservation(activeSchedule, passenger, passenger.getFullName(),
                passenger.getPhone(), new BigDecimal("3300.00"), Reservation.ReservationStatus.CONFIRMED));

        mvc.perform(post("/api/eer/reservations/" + unpaid.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Cancel unpaid reservation"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/eer/reservations/" + unpaid.getId() + "/adjudicate-cancellation?approve=true")
                        .cookie(userCookie(supervisor.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulatedRefundAmount").value(0));

        assertTrue(payments.findByReservationId(unpaid.getId()).isEmpty());
        var afterReport = json.readTree(mvc.perform(get("/api/eer/financial-report")
                .cookie(userCookie(financeManager.getUser()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, afterReport.path("totalRefunded").decimalValue()
                .compareTo(beforeReport.path("totalRefunded").decimalValue()));
        assertEquals(0, afterReport.path("totalRevenue").decimalValue()
                .compareTo(beforeReport.path("totalRevenue").decimalValue()));
    }

    @Test
    @DisplayName("Negative Flow: Cannot cancel departed trip or checked-in ticket")
    void testCannotCancelDepartedOrCheckedInTrip() throws Exception {
        // Departed schedule
        Reservation departedRes = createConfirmedReservation(passenger, departedSchedule, new BigDecimal("3300.00"), "16");
        mvc.perform(post("/api/eer/reservations/" + departedRes.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Missed my bus"))))
                .andExpect(status().isBadRequest());

        // Checked-in ticket
        Reservation checkedInRes = createConfirmedReservation(passenger, activeSchedule, new BigDecimal("3300.00"), "17");
        Ticket ticket = new Ticket();
        ticket.setReservation(checkedInRes);
        ticket.setQrCode("QR-DATA-1");
        ticket.setCheckedInAt(LocalDateTime.now().minusMinutes(10));
        tickets.save(ticket);

        mvc.perform(post("/api/eer/reservations/" + checkedInRes.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Already on bus"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Duplicate Request Protection: Idempotent prevention of multiple pending cancellation requests")
    void testDuplicateRequestProtection() throws Exception {
        Reservation r = createConfirmedReservation(passenger, activeSchedule, new BigDecimal("3300.00"), "18");

        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "First request"))))
                .andExpect(status().isOk());

        // Duplicate submission while first is still pending
        mvc.perform(post("/api/eer/reservations/" + r.getId() + "/cancel-request")
                        .cookie(userCookie(passenger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Duplicate request"))))
                .andExpect(status().isConflict());
    }
}
