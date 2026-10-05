package com.ciao.backend;

import com.ciao.backend.dto.BusRequest;
import com.ciao.backend.dto.ScheduleRequest;
import com.ciao.backend.dto.groupbooking.GroupBookingResponse;
import com.ciao.backend.dto.reservation.ReservationRequest;
import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.BusService;
import com.ciao.backend.service.EerService;
import com.ciao.backend.service.ReservationService;
import com.ciao.backend.service.ScheduleService;
import com.ciao.backend.service.GroupBookingService;
import com.ciao.backend.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ciao.backend.dto.reservation.PaymentRequest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EerConcurrencyIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private BusRepository buses;
    @Autowired private BusSeatRepository busSeats;
    @Autowired private RouteRepository routes;
    @Autowired private ScheduleRepository schedules;
    @Autowired private ReservationRepository reservations;
    @Autowired private GroupBookingRepository groupBookings;
    @Autowired private StaffProfileRepository staff;
    @Autowired private CustomerProfileRepository customers;
    @Autowired private BusService busService;
    @Autowired private ReservationService reservationService;
    @Autowired private EerService eerService;
    @Autowired private ReservedSeatRepository reservedSeats;
    @Autowired private PaymentRepository payments;
    @Autowired private NotificationRepository notifications;
    @Autowired private BookingRepository bookings;
    @Autowired private ScheduleService scheduleService;
    @Autowired private GroupBookingService groupBookingService;
    @Autowired private com.ciao.backend.service.PaymentService paymentService;
    @Autowired private TicketRepository tickets;
    @Autowired private LostItemRepository lostItems;
    @Autowired private LostItemClaimRepository claims;
    @Autowired private BranchRepository branches;
    @Autowired private com.ciao.backend.controller.EerController eerController;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User createUser(String roleName, String rawPassword) {
        User u = new User();
        u.setFullName("User " + roleName);
        u.setUsername("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        u.setEmail(UUID.randomUUID() + "@ciao.test");
        u.setPhone("077" + (int)(1000000 + Math.random() * 8999999));
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(roles.findByRoleName(roleName).orElseGet(() -> roles.save(new Role(null, roleName))));
        return users.save(u);
    }

    private Cookie login(User user, String rawPassword) throws Exception {
        var res = mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", user.getEmail(), "password", rawPassword))))
                .andReturn().getResponse();
        return res.getCookie("ciao_jwt");
    }

    @Test
    void testConcurrentCapacityReductionVsSeatHold() throws Exception {
        Route route = new Route();
        route.setOrigin("Kandy");
        route.setDestination("Jaffna");
        route.setDistanceKm(BigDecimal.valueOf(320.0));
        route.setBaseFare(BigDecimal.valueOf(1800.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = new Bus();
        bus.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("LUXURY");
        bus.setCapacity(50);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);
        eerService.syncSeats(bus);

        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setBus(bus);
        schedule.setDepartureTime(LocalDateTime.now().plusDays(4));
        schedule.setArrivalTime(LocalDateTime.now().plusDays(4).plusHours(6));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        final Integer busId = bus.getId();
        final Integer scheduleId = schedule.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean seatHoldSucceeded = new AtomicBoolean(false);
        AtomicBoolean capacityReduced = new AtomicBoolean(false);

        CompletableFuture<Void> capacityTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                BusRequest req = new BusRequest();
                req.setPlateNumber(buses.findById(busId).get().getPlateNumber());
                req.setCapacity(40);
                req.setStatus(Bus.BusStatus.ACTIVE);
                busService.updateBus(busId, req);
                capacityReduced.set(true);
            } catch (Exception ignored) {
                // Expected if seat hold acquired the lock first
            }
        });

        CompletableFuture<Void> holdTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                ReservationRequest holdReq = new ReservationRequest();
                holdReq.setScheduleId(scheduleId);
                holdReq.setSeatNumbers(List.of("48"));
                holdReq.setPassengerName("Race Passenger");
                holdReq.setPassengerPhone("0778889900");
                reservationService.lockSeats(holdReq, null);
                seatHoldSucceeded.set(true);
            } catch (Exception ignored) {
                // Expected if capacity reduction won the race
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(capacityTask, holdTask).join();

        // 1. Strict XOR: Both conflicting operations cannot succeed, and at least one must succeed
        assertTrue(capacityReduced.get() ^ seatHoldSucceeded.get(),
                "Conflicting capacity reduction and seat hold cannot both succeed or both fail");

        Bus finalBus = buses.findById(busId).orElseThrow();
        List<BusSeat> seatsList = busSeats.findByBusId(busId);
        BusSeat seat48 = seatsList.stream()
                .filter(s -> "48".equals(s.getSeatNumber()))
                .findFirst()
                .orElse(null);
        assertNotNull(seat48);

        if (capacityReduced.get()) {
            assertEquals(40, finalBus.getCapacity());
            assertEquals("INACTIVE", seat48.getSeatStatus());
            assertTrue(reservedSeats.findAllUnavailableSeatsForSchedule(scheduleId, LocalDateTime.now()).isEmpty(),
                    "When capacity reduction wins, no active allocation for seat 48 may exist");
        } else {
            assertEquals(50, finalBus.getCapacity());
            assertEquals("ACTIVE", seat48.getSeatStatus());
            assertEquals(List.of("48"), reservedSeats.findAllUnavailableSeatsForSchedule(scheduleId, LocalDateTime.now()),
                    "When seat hold wins, seat 48 must remain held and capacity must not reduce below it");
        }

        // 2. Controlled Sequential Synchronization:
        // Order A: Existing hold on high seat strictly rejects capacity reduction below that seat
        Bus busA = new Bus("SEQ-A-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 50, "AC", Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);
        Schedule schA = schedules.save(new Schedule(route, busA, null, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(4), Schedule.ScheduleStatus.SCHEDULED));

        ReservationRequest holdA = new ReservationRequest();
        holdA.setScheduleId(schA.getId());
        holdA.setSeatNumbers(List.of("47"));
        holdA.setPassengerName("Sequential Passenger");
        holdA.setPassengerPhone("0771112233");
        var holdARes = reservationService.lockSeats(holdA, null);

        final Integer busAId = busA.getId();
        BusRequest redA = new BusRequest();
        redA.setPlateNumber(busA.getPlateNumber());
        redA.setCapacity(40);
        redA.setStatus(Bus.BusStatus.ACTIVE);
        assertThrows(RuntimeException.class, () -> busService.updateBus(busAId, redA),
                "Capacity reduction below active held seat 47 must be rejected");

        // Order B: Reduced capacity strictly rejects subsequent holds on reduced seats
        Bus busB = new Bus("SEQ-B-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);
        Schedule schB = schedules.save(new Schedule(route, busB, null, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(4), Schedule.ScheduleStatus.SCHEDULED));

        ReservationRequest holdB = new ReservationRequest();
        holdB.setScheduleId(schB.getId());
        holdB.setSeatNumbers(List.of("45"));
        holdB.setPassengerName("Out of Range Passenger");
        holdB.setPassengerPhone("0772223344");
        assertThrows(RuntimeException.class, () -> reservationService.lockSeats(holdB, null),
                "Seat hold for seat 45 must be rejected when bus capacity is 40");

        // Capacity vs Payment verification: hold valid seat 35, reduce capacity from 50 to 40 (valid), then pay -> succeeds
        ReservationRequest holdValid = new ReservationRequest();
        holdValid.setScheduleId(schA.getId());
        holdValid.setSeatNumbers(List.of("35"));
        holdValid.setPassengerName("Payment Passenger");
        holdValid.setPassengerPhone("0773334455");
        var resHold = reservationService.lockSeats(holdValid, null);

        // Cancel hold 47 to allow capacity reduction to 40
        List<com.ciao.backend.entity.ReservedSeat> seats47 = reservedSeats.findByReservationId(holdARes.getId());
        for (com.ciao.backend.entity.ReservedSeat rs : seats47) {
            if ("47".equals(rs.getSeatNumber())) {
                rs.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
                reservedSeats.save(rs);
            }
        }

        redA.setCapacity(40);
        busService.updateBus(busAId, redA);
        assertEquals(40, buses.findById(busAId).get().getCapacity());

        // Payment for held seat 35 succeeds
        var checkoutReq = new PaymentRequest();
        checkoutReq.setReservationId(resHold.getId());
        checkoutReq.setCardNumber("4111111111111111");
        checkoutReq.setCardholderName("CIAO TEST");
        checkoutReq.setExpiry("12/30");
        checkoutReq.setCvv("123");
        var payRes = paymentService.processCheckout(checkoutReq, null, resHold.getId());
        assertEquals("SUCCESS", payRes.getStatus());
    }

    @Test
    void testCharterConversionBookingScenarios() throws Exception {
        User staffUser = createUser("STAFF", "OpsPass123");
        eerService.profile(staffUser);
        StaffProfile sp = staff.findByUserId(staffUser.getId()).orElseThrow();
        sp.setStaffType("OPERATIONS_MANAGER");
        staff.save(sp);
        Cookie opsCookie = login(staffUser, "OpsPass123");

        User customerUser = createUser("PASSENGER", "CharterCust");
        CustomerProfile cp = eerService.customer(customerUser);

        Route route = routes.save(new Route("Charter-" + UUID.randomUUID(), "Destination", new BigDecimal("1000"), Route.RouteStatus.ACTIVE));
        Bus bus = buses.save(new Bus("CH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "LUXURY", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);

        Schedule schedule = new Schedule(route, bus, null, LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(7).plusHours(5), Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Charter Club");
        charter.setCustomerPhone("0773335555");
        charter.setStartDate(schedule.getDepartureTime());
        charter.setEndDate(schedule.getArrivalTime());
        charter.setPassengerCount(30);
        charter.setTotalCost(BigDecimal.valueOf(80000.0));
        charter.setDepositAmount(BigDecimal.valueOf(24000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        // 1. Booking BEFORE charter conversion: active hold blocks conversion
        ReservationRequest preHold = new ReservationRequest();
        preHold.setScheduleId(schedule.getId());
        preHold.setSeatNumbers(List.of("1"));
        preHold.setPassengerName("Early Passenger");
        preHold.setPassengerPhone("0779998877");
        var holdResult = reservationService.lockSeats(preHold, null);

        // Attempting charter conversion while active hold exists -> rejected
        final Integer cId = charter.getId();
        final Integer sId = schedule.getId();
        final Integer cpId = cp.getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + cId)
                .cookie(opsCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cpId,
                        "scheduleId", sId,
                        "eventType", "University Field Trip"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // Cancel seat hold so schedule becomes clean
        Reservation res = reservations.findById(holdResult.getId()).orElseThrow();
        res.setStatus(Reservation.ReservationStatus.CANCELLED);
        reservations.save(res);
        for (ReservedSeat rs : reservedSeats.findByReservationId(res.getId())) {
            rs.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
            reservedSeats.save(rs);
        }

        // 2. Conversion succeeds when no active passengers exist
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + cId)
                .cookie(opsCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cpId,
                        "scheduleId", sId,
                        "eventType", "University Field Trip"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        Schedule charteredSchedule = schedules.findById(sId).orElseThrow();
        assertTrue(charteredSchedule.isCharter(), "Schedule must have isCharter=true after charter conversion");

        // 3. Excluded from public search
        List<Schedule> searchResults = scheduleService.searchSchedules(route.getOrigin(), "Destination", schedule.getDepartureTime().toLocalDate());
        assertTrue(searchResults.stream().noneMatch(s -> s.getId().equals(sId)),
                "Chartered trip must be excluded from public search results");

        // 4. Direct seat-map returns bookable=false and isCharter=true
        var seatMap = reservationService.getSeatMap(sId);
        assertFalse((Boolean) seatMap.get("bookable"));
        assertTrue((Boolean) seatMap.get("isCharter"));

        // 5. Direct seat hold attempt is rejected
        ReservationRequest directHold = new ReservationRequest();
        directHold.setScheduleId(sId);
        directHold.setSeatNumbers(List.of("2"));
        directHold.setPassengerName("Late Public Passenger");
        directHold.setPassengerPhone("0778887766");
        assertThrows(RuntimeException.class, () -> reservationService.lockSeats(directHold, null),
                "Direct seat hold on chartered trip must throw descriptive exception");

        // 6. Cancellation safely releases charter flag
        var cancelReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        cancelReq.setStatus("CANCELLED");
        groupBookingService.updateBookingStatus(cId, cancelReq);

        Schedule releasedSchedule = schedules.findById(sId).orElseThrow();
        assertFalse(releasedSchedule.isCharter(), "Cancelling charter must release isCharter=false");
    }

    @Test
    void testConcurrentCharterConversionVsSeatHold() throws Exception {
        User staffUser = createUser("STAFF", "OpsPassRace");
        eerService.profile(staffUser);
        StaffProfile sp = staff.findByUserId(staffUser.getId()).orElseThrow();
        sp.setStaffType("OPERATIONS_MANAGER");
        staff.save(sp);
        Cookie opsCookie = login(staffUser, "OpsPassRace");

        User customerUser = createUser("PASSENGER", "RaceCust");
        CustomerProfile cp = eerService.customer(customerUser);

        Route route = routes.save(new Route("RaceRoute-" + UUID.randomUUID(), "Destination", new BigDecimal("1200"), Route.RouteStatus.ACTIVE));
        Bus bus = buses.save(new Bus("RC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "LUXURY", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);

        Schedule schedule = new Schedule(route, bus, null, LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(8).plusHours(5), Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Race Group");
        charter.setCustomerPhone("0779991111");
        charter.setStartDate(schedule.getDepartureTime());
        charter.setEndDate(schedule.getArrivalTime());
        charter.setPassengerCount(25);
        charter.setTotalCost(BigDecimal.valueOf(90000.0));
        charter.setDepositAmount(BigDecimal.valueOf(27000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        final Integer cId = charter.getId();
        final Integer sId = schedule.getId();
        final Integer cpId = cp.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean holdWon = new AtomicBoolean(false);
        AtomicBoolean charterWon = new AtomicBoolean(false);

        CompletableFuture<Void> holdTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                ReservationRequest req = new ReservationRequest();
                req.setScheduleId(sId);
                req.setSeatNumbers(List.of("5"));
                req.setPassengerName("Race Seat Passenger");
                req.setPassengerPhone("0771234567");
                reservationService.lockSeats(req, null);
                holdWon.set(true);
            } catch (Exception ignored) {
                // Expected if charter conversion won first
            }
        });

        CompletableFuture<Void> charterTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + cId)
                        .cookie(opsCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "customerId", cpId,
                                "scheduleId", sId,
                                "eventType", "Concurrent Charter Race"
                        ))))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() == 200) {
                                charterWon.set(true);
                            }
                        });
            } catch (Exception ignored) {
                // Expected if hold won first
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(holdTask, charterTask).join();

        // Exactly one conflicting operation wins
        assertTrue(holdWon.get() ^ charterWon.get(), "Conflicting seat hold and charter conversion cannot both succeed or fail");

        Schedule finalSch = schedules.findById(sId).orElseThrow();
        if (charterWon.get()) {
            assertTrue(finalSch.isCharter());
            assertTrue(reservedSeats.findAllUnavailableSeatsForSchedule(sId, LocalDateTime.now()).isEmpty());
        } else {
            assertFalse(finalSch.isCharter());
            assertEquals(List.of("5"), reservedSeats.findAllUnavailableSeatsForSchedule(sId, LocalDateTime.now()));
        }
    }

    @Test
    void testConcurrentRecurringGenerationVsCharterAssignment() throws Exception {
        User staffUser = createUser("STAFF", "OpsRecRace");
        eerService.profile(staffUser);
        StaffProfile sp = staff.findByUserId(staffUser.getId()).orElseThrow();
        sp.setStaffType("OPERATIONS_MANAGER");
        staff.save(sp);
        Cookie opsCookie = login(staffUser, "OpsRecRace");

        User customerUser = createUser("PASSENGER", "RecCharterCust");
        CustomerProfile cp = eerService.customer(customerUser);

        Bus bus = buses.save(new Bus("RRG-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "SUPER_LUXURY", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);

        Route route = routes.save(new Route("RecRace-" + UUID.randomUUID(), "Destination", new BigDecimal("850"), Route.RouteStatus.ACTIVE));
        LocalDate day12 = LocalDate.now().plusDays(12);
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(10, 0);
        Schedule template = new Schedule(route, bus, null, departure, departure.plusHours(4), Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(null); // Indefinite recurring service
        template = schedules.save(template);

        // Candidate charter on same bus and overlapping timeslot on Day 12
        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Rec Group Race");
        charter.setCustomerPhone("0778881234");
        charter.setStartDate(day12.atTime(11, 0));
        charter.setEndDate(day12.atTime(13, 0));
        charter.setPassengerCount(25);
        charter.setTotalCost(BigDecimal.valueOf(75000.0));
        charter.setDepositAmount(BigDecimal.valueOf(22500.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        final Integer cId = charter.getId();
        final Integer tId = template.getId();
        final Integer bId = bus.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean occurrenceCreated = new AtomicBoolean(false);
        AtomicBoolean charterAssigned = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> charterException = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> recurringException = new java.util.concurrent.atomic.AtomicReference<>();

        CompletableFuture<Void> recurringTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                scheduleService.materializeSingleTemplateOccurrence(tId, day12);
                if (schedules.findByRecurrenceParentIdAndDepartureTime(tId, day12.atTime(10, 0)).isPresent()) {
                    occurrenceCreated.set(true);
                }
            } catch (Throwable t) {
                recurringException.set(t);
            }
        });

        CompletableFuture<Void> charterTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var statusUpdate = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
                statusUpdate.setStatus("APPROVED");
                statusUpdate.setAssignedBusId(bId);
                groupBookingService.updateBookingStatus(cId, statusUpdate);
                charterAssigned.set(true);
            } catch (Throwable t) {
                charterException.set(t);
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(recurringTask, charterTask).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Fail on unexpected exception in recurring task
        if (recurringException.get() != null) {
            fail("Recurring occurrence generation failed unexpectedly: " + recurringException.get().getMessage());
        }

        // The recurring service template already owns the bus on Day 12.
        // Therefore, the charter assignment targeting that slot MUST be rejected by centralized bus conflict validation,
        // and the recurring occurrence MUST be created and committed in the database.
        assertTrue(occurrenceCreated.get(), "Recurring occurrence must be successfully created");
        assertFalse(charterAssigned.get(), "Charter assignment must be rejected because bus is already owned by recurring service");
        assertNotNull(charterException.get(), "Charter task must fail with a business validation exception");
        assertTrue(charterException.get().getMessage().contains("overlapping daily service") || charterException.get().getMessage().contains("Bus"),
                "Expected conflict exception message, got: " + charterException.get().getMessage());

        // Assert committed database state
        assertTrue(schedules.findByRecurrenceParentIdAndDepartureTime(tId, day12.atTime(10, 0)).isPresent(),
                "Committed database must have the occurrence on Day 12");
        GroupBooking committedCharter = groupBookings.findById(cId).orElseThrow();
        assertNotEquals(GroupBooking.GroupBookingStatus.APPROVED, committedCharter.getStatus(),
                "Committed charter status must NOT be APPROVED");

        // Part 2: Reverse scenario - When charter is already committed for a slot, recurring generator safely skips materialization
        LocalDate day18 = LocalDate.now().plusDays(18);
        GroupBooking approvedCharter = new GroupBooking();
        approvedCharter.setCustomerName("Prior Charter");
        approvedCharter.setCustomerPhone("0779998888");
        approvedCharter.setStartDate(day18.atTime(9, 0));
        approvedCharter.setEndDate(day18.atTime(15, 0));
        approvedCharter.setPassengerCount(30);
        approvedCharter.setTotalCost(BigDecimal.valueOf(80000.0));
        approvedCharter.setDepositAmount(BigDecimal.valueOf(24000.0));
        approvedCharter.setAssignedBus(bus);
        approvedCharter.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        approvedCharter = groupBookings.save(approvedCharter);
        eerService.syncGroup(approvedCharter);

        // Materialization should not throw and should safely skip creating an overlapping occurrence
        scheduleService.materializeSingleTemplateOccurrence(tId, day18);
        assertTrue(schedules.findByRecurrenceParentIdAndDepartureTime(tId, day18.atTime(10, 0)).isEmpty(),
                "Occurrence must NOT be created when an approved charter already owns the bus slot");

        // Part 3: Concurrent schedule edit of recurring template vs occurrence generation
        // Create a fresh template without prior departures for this concurrent test
        Bus bus3 = new Bus();
        bus3.setPlateNumber("RAC-EDIT-" + UUID.randomUUID().toString().substring(0, 4));
        bus3.setCapacity(40);
        bus3.setStatus(Bus.BusStatus.ACTIVE);
        bus3 = buses.save(bus3);

        Schedule template3 = new Schedule();
        template3.setRoute(route);
        template3.setBus(bus3);
        template3.setDepartureTime(departure.plusDays(40));
        template3.setArrivalTime(departure.plusDays(40).plusHours(4));
        template3.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        template3.setRepeatDaily(true);
        template3.setRepeatUntil(LocalDate.now().plusDays(60));
        template3 = schedules.save(template3);

        final Integer templateIdFinal = template3.getId();
        final Integer bus3Id = bus3.getId();
        final LocalDateTime dep3 = template3.getDepartureTime();
        CountDownLatch editLatch = new CountDownLatch(1);
        AtomicBoolean editDone = new AtomicBoolean(false);
        AtomicBoolean editRejectedDueToDepartures = new AtomicBoolean(false);
        AtomicBoolean genDone = new AtomicBoolean(false);

        var editFuture = CompletableFuture.runAsync(() -> {
            try {
                editLatch.await();
                var req = new com.ciao.backend.dto.ScheduleRequest();
                req.setRouteId(route.getId());
                req.setBusId(bus3Id);
                req.setDepartureTime(dep3);
                req.setArrivalTime(dep3.plusHours(4));
                req.setStatus("SCHEDULED");
                req.setRepeatDaily(true);
                req.setRepeatUntil(LocalDate.now().plusDays(70));
                scheduleService.updateSchedule(templateIdFinal, req);
                editDone.set(true);
            } catch (org.springframework.web.server.ResponseStatusException rse) {
                if (rse.getStatusCode().value() == 400 && rse.getReason() != null
                        && rse.getReason().contains("already has dated departures")) {
                    editRejectedDueToDepartures.set(true);
                } else {
                    fail("Unexpected schedule edit error: " + rse.getMessage());
                }
            } catch (Exception e) {
                fail("Schedule edit failed unexpectedly: " + e.getMessage());
            }
        });

        var genFuture = CompletableFuture.runAsync(() -> {
            try {
                editLatch.await();
                scheduleService.materializeSingleTemplateOccurrence(templateIdFinal, LocalDate.now().plusDays(20));
                genDone.set(true);
            } catch (Exception e) {
                fail("Occurrence generation failed: " + e.getMessage());
            }
        });

        editLatch.countDown();
        CompletableFuture.allOf(editFuture, genFuture).get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(genDone.get(), "Occurrence generation must succeed");
        assertTrue(editDone.get() || editRejectedDueToDepartures.get(),
                "Template edit must either succeed before generation or be rejected with business rule when occurrence was created first");
    }

    @Test
    void testGuestTokenRecoveryAccessControl() throws Exception {
        GroupBooking guestBooking = new GroupBooking();
        guestBooking.setCustomerName("Guest Traveler");
        guestBooking.setCustomerPhone("0775556677");
        guestBooking.setStartDate(LocalDateTime.now().plusDays(6));
        guestBooking.setEndDate(LocalDateTime.now().plusDays(6).plusHours(6));
        guestBooking.setPassengerCount(20);
        guestBooking.setTotalCost(BigDecimal.valueOf(50000.0));
        guestBooking.setDepositAmount(BigDecimal.valueOf(15000.0));
        guestBooking.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        guestBooking.setGuestAccessToken("secret-guest-token-12345");
        guestBooking = groupBookings.save(guestBooking);
        eerService.syncGroup(guestBooking);

        final String reference = "GRP-" + guestBooking.getId();

        // 1. Unauthenticated guest self-recovery attempt is rejected (HTTP 401)
        mvc.perform(post("/api/group-bookings/recover-token")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "reference", reference,
                        "phone", "0775556677",
                        "name", "Guest Traveler"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());

        // 2. Unauthorized staff (Branch Manager) is rejected (HTTP 403 Forbidden)
        User branchMgr = createUser("STAFF", "BranchPass123");
        eerService.profile(branchMgr);
        StaffProfile sp = staff.findByUserId(branchMgr.getId()).orElseThrow();
        sp.setStaffType("BRANCH_MANAGER");
        staff.save(sp);
        Cookie branchCookie = login(branchMgr, "BranchPass123");

        mvc.perform(post("/api/group-bookings/recover-token")
                .cookie(branchCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "reference", reference,
                        "phone", "0775556677",
                        "name", "Guest Traveler"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());

        // 3. Authorized staff (Operations Manager)
        User opsUser = createUser("STAFF", "OpsAuth123");
        eerService.profile(opsUser);
        StaffProfile opsSp = staff.findByUserId(opsUser.getId()).orElseThrow();
        opsSp.setStaffType("OPERATIONS_MANAGER");
        staff.save(opsSp);
        Cookie opsCookie = login(opsUser, "OpsAuth123");

        // Wrong phone -> 404 Not Found
        mvc.perform(post("/api/group-bookings/recover-token")
                .cookie(opsCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "reference", reference,
                        "phone", "0770000000",
                        "name", "Guest Traveler"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());

        // Correct credentials -> 200 OK with token
        var result = mvc.perform(post("/api/group-bookings/recover-token")
                .cookie(opsCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "reference", reference,
                        "phone", "0775556677",
                        "name", "Guest Traveler"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("secret-guest-token-12345"));

        // 4. Restored token can be used through the intended guest deposit payment flow
        guestBooking.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        groupBookings.save(guestBooking);

        mvc.perform(post("/api/eer/groups/pay-deposit")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "groupId", guestBooking.getId(),
                        "guestToken", "secret-guest-token-12345",
                        "cardNumber", "4111111111111111",
                        "cardholderName", "CIAO TEST",
                        "expiry", "12/30",
                        "cvv", "123"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        GroupBooking paidGuest = groupBookings.findById(guestBooking.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.DEPOSIT_PAID, paidGuest.getStatus());
    }

    @Test
    void testConcurrentGroupDepositAtomicity() throws Exception {
        User staffUser = createUser("STAFF", "FinancePass123");
        eerService.profile(staffUser);
        StaffProfile sp = staff.findByUserId(staffUser.getId()).orElseThrow();
        sp.setStaffType("FINANCE_MANAGER");
        staff.save(sp);
        Cookie financeCookie = login(staffUser, "FinancePass123");

        User customerUser = createUser("PASSENGER", "CharterCustomer");
        CustomerProfile cp = eerService.customer(customerUser);

        Bus bus = new Bus();
        bus.setPlateNumber("ND-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("SUPER_LUXURY");
        bus.setCapacity(45);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Concurrent Org");
        charter.setCustomerPhone("0771239999");
        charter.setStartDate(LocalDateTime.now().plusDays(10));
        charter.setEndDate(LocalDateTime.now().plusDays(10).plusHours(8));
        charter.setPassengerCount(35);
        charter.setTotalCost(BigDecimal.valueOf(100000.0));
        charter.setDepositAmount(BigDecimal.valueOf(30000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        charter.setAssignedBus(bus);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        // Bind booking to real customer
        Booking parentBooking = charter.getBooking();
        parentBooking.setCustomer(cp);
        bookings.save(parentBooking);

        final Integer charterId = charter.getId();
        int threads = 4;
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(CompletableFuture.runAsync(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    mvc.perform(post("/api/eer/groups/pay-deposit")
                            .cookie(financeCookie)
                            .contentType("application/json")
                            .content(json.writeValueAsString(Map.of(
                                    "groupId", charterId,
                                    "cardNumber", "4111111111111111",
                                    "cardholderName", "CIAO TEST",
                                    "expiry", "12/30",
                                    "cvv", "123"
                            ))))
                            .andDo(result -> {
                                int status = result.getResponse().getStatus();
                                if (status == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    rejectedCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

        assertEquals(1, successCount.get(), "Exactly one concurrent deposit payment must succeed");
        assertEquals(threads - 1, rejectedCount.get(), "Subsequent concurrent attempts must be rejected");

        // Database-side uniqueness assertions:
        GroupBooking finalCharter = groupBookings.findById(charterId).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.DEPOSIT_PAID, finalCharter.getStatus());
        assertEquals("DEPOSIT_PAID", finalCharter.getBooking().getBookingStatus());

        // Exactly one Payment in DB
        List<Payment> bookingPayments = payments.findAll().stream()
                .filter(p -> p.getBooking() != null && p.getBooking().getId().equals(finalCharter.getBooking().getId()))
                .toList();
        assertEquals(1, bookingPayments.size(), "Database must have exactly 1 payment record for this group booking");
        Payment payment = bookingPayments.get(0);
        assertEquals(Payment.PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("GROUP_DEPOSIT", payment.getPaymentType());
        assertEquals(new BigDecimal("30000.00"), payment.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));

        // Exactly one Notification for the customer
        List<Notification> customerNotifications = notifications.findAll().stream()
                .filter(n -> n.getUser() != null && n.getUser().getId().equals(customerUser.getId()))
                .toList();
        assertEquals(1, customerNotifications.size(), "Exactly one confirmation notification must be created for customer");

        // Retry must return HTTP 400 Bad Request, NOT HTTP 500, and create no duplicate records
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .cookie(financeCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "groupId", charterId,
                        "cardNumber", "4111111111111111",
                        "cardholderName", "CIAO TEST",
                        "expiry", "12/30",
                        "cvv", "123"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // Payments count remains exactly 1 after retry
        long paymentCountAfterRetry = payments.findAll().stream()
                .filter(p -> p.getBooking() != null && p.getBooking().getId().equals(finalCharter.getBooking().getId()))
                .count();
        assertEquals(1, paymentCountAfterRetry);
    }

    @Test
    void testConcurrentCapacityReductionVsCheckout() throws Exception {
        Route route = new Route();
        route.setOrigin("CapCheckout-Org");
        route.setDestination("CapCheckout-Dst");
        route.setDistanceKm(BigDecimal.valueOf(120.0));
        route.setBaseFare(BigDecimal.valueOf(1500.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = buses.save(new Bus("CAP-CHK-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 50, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);
        Schedule sch = schedules.save(new Schedule(route, bus, null, LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(8).plusHours(4), Schedule.ScheduleStatus.SCHEDULED));

        // Passenger locks seat 48
        ReservationRequest holdReq = new ReservationRequest();
        holdReq.setScheduleId(sch.getId());
        holdReq.setSeatNumbers(List.of("48"));
        holdReq.setPassengerName("Checkout Passenger");
        holdReq.setPassengerPhone("0778889900");
        var holdRes = reservationService.lockSeats(holdReq, null);

        // Concurrent execution: Thread 1 attempts checkout payment for seat 48, Thread 2 attempts capacity reduction to 40
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean checkoutWon = new AtomicBoolean(false);
        AtomicBoolean reductionWon = new AtomicBoolean(false);

        CompletableFuture<Void> checkoutTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                PaymentRequest payReq = new PaymentRequest();
                payReq.setReservationId(holdRes.getId());
                payReq.setCardNumber("4111111111111111");
                payReq.setCardholderName("CIAO TEST");
                payReq.setExpiry("12/30");
                payReq.setCvv("123");
                var payRes = paymentService.processCheckout(payReq, null, holdRes.getId());
                if ("SUCCESS".equals(payRes.getStatus())) {
                    checkoutWon.set(true);
                }
            } catch (Exception ignored) {}
        });

        CompletableFuture<Void> reductionTask = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                BusRequest redReq = new BusRequest();
                redReq.setPlateNumber(bus.getPlateNumber());
                redReq.setCapacity(40);
                redReq.setStatus(Bus.BusStatus.ACTIVE);
                busService.updateBus(bus.getId(), redReq);
                reductionWon.set(true);
            } catch (Exception ignored) {}
        });

        startLatch.countDown();
        CompletableFuture.allOf(checkoutTask, reductionTask).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Invariant: Seat 48 is either actively held or booked. Capacity reduction to 40 MUST NOT silently succeed alongside a held/booked seat 48.
        assertTrue(checkoutWon.get() ^ reductionWon.get(),
                "Exactly one conflicting operation must win: either checkout confirms seat 48, or capacity reduction reduces to 40");

        Bus committedBus = buses.findById(bus.getId()).orElseThrow();
        if (checkoutWon.get()) {
            assertEquals(50, committedBus.getCapacity(), "Bus capacity must remain 50 when checkout won");
            Reservation confirmedRes = reservations.findById(holdRes.getId()).orElseThrow();
            assertEquals(Reservation.ReservationStatus.CONFIRMED, confirmedRes.getStatus());
        } else {
            assertEquals(40, committedBus.getCapacity(), "Bus capacity must be reduced to 40 when reduction won");
        }
    }

    @Test
    void testCharterConversionAndReassignmentLifecycle() throws Exception {
        Route route = new Route();
        route.setOrigin("CharterLife-Org");
        route.setDestination("CharterLife-Dst");
        route.setDistanceKm(BigDecimal.valueOf(100.0));
        route.setBaseFare(BigDecimal.valueOf(1200.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = buses.save(new Bus("CHA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 45, "AC", Bus.BusStatus.ACTIVE));
        Bus busB = buses.save(new Bus("CHB-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 45, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(busA);
        eerService.syncSeats(busB);

        LocalDateTime depA = LocalDateTime.now().plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime depB = LocalDateTime.now().plusDays(10).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime depC = LocalDateTime.now().plusDays(10).withHour(19).withMinute(0).withSecond(0).withNano(0);

        Schedule schA = schedules.save(new Schedule(route, busA, null, depA, depA.plusHours(4), Schedule.ScheduleStatus.SCHEDULED));
        Schedule schB = schedules.save(new Schedule(route, busB, null, depB, depB.plusHours(4), Schedule.ScheduleStatus.SCHEDULED));
        Schedule schC = schedules.save(new Schedule(route, busA, null, depC, depC.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));

        User opUser = createUser("STAFF", "Secret123!");
        eerService.profile(opUser);
        StaffProfile opStaff = staff.findByUserId(opUser.getId()).orElseThrow();
        opStaff.setStaffType("OPERATIONS_MANAGER");
        staff.save(opStaff);
        Cookie opCookie = login(opUser, "Secret123!");

        User custUser = createUser("PASSENGER", "Secret123!");
        CustomerProfile cust = eerService.customer(custUser);
        cust.setFirstName("Life");
        cust.setLastName("Traveler");
        cust.setAddress("123 Street");
        customers.save(cust);

        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Lifecycle Group");
        charter.setCustomerPhone("0774443322");
        charter.setStartDate(depA);
        charter.setEndDate(depA.plusHours(4));
        charter.setPassengerCount(30);
        charter.setTotalCost(BigDecimal.valueOf(60000.0));
        charter.setDepositAmount(BigDecimal.valueOf(18000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        // 1. Initial Assignment to Schedule A
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + charter.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schA.getId(),
                        "eventType", "Corporate Retreat"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // Assert Schedule A is charter and excluded from public search
        Schedule lockedA = schedules.findById(schA.getId()).orElseThrow();
        assertTrue(lockedA.isCharter());
        List<Schedule> searchA = schedules.searchSchedules("CharterLife-Org", "CharterLife-Dst", LocalDateTime.now(), depA.minusHours(1), depA.plusHours(1));
        assertTrue(searchA.stream().noneMatch(s -> s.getId().equals(schA.getId())), "Schedule A must be excluded from public search");

        // Direct seat hold on Schedule A must be rejected
        ReservationRequest directHoldA = new ReservationRequest();
        directHoldA.setScheduleId(schA.getId());
        directHoldA.setSeatNumbers(List.of("10"));
        directHoldA.setPassengerName("Direct Guest");
        directHoldA.setPassengerPhone("0771112233");
        assertThrows(RuntimeException.class, () -> reservationService.lockSeats(directHoldA, null));

        // 2. Reassignment from Schedule A to Schedule B
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + charter.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schB.getId(),
                        "eventType", "Corporate Retreat Shifted"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // Assert Schedule A was safely released (isCharter = false) and Schedule B is now charter
        lockedA = schedules.findById(schA.getId()).orElseThrow();
        assertFalse(lockedA.isCharter(), "Schedule A must be released when reassigned to B");
        Schedule lockedB = schedules.findById(schB.getId()).orElseThrow();
        assertTrue(lockedB.isCharter(), "Schedule B must be marked as charter");

        // Public search now finds Schedule A, but excludes Schedule B
        searchA = schedules.searchSchedules("CharterLife-Org", "CharterLife-Dst", LocalDateTime.now(), depA.minusHours(1), depA.plusHours(1));
        assertTrue(searchA.stream().anyMatch(s -> s.getId().equals(schA.getId())), "Released Schedule A must reappear in public search");
        List<Schedule> searchB = schedules.searchSchedules("CharterLife-Org", "CharterLife-Dst", LocalDateTime.now(), depB.minusHours(1), depB.plusHours(1));
        assertTrue(searchB.stream().noneMatch(s -> s.getId().equals(schB.getId())), "Assigned Schedule B must be excluded from public search");

        // 3. Failed Reassignment to Schedule C (active seat hold on C)
        ReservationRequest holdC = new ReservationRequest();
        holdC.setScheduleId(schC.getId());
        holdC.setSeatNumbers(List.of("5"));
        holdC.setPassengerName("Public Seat Hold Passenger");
        holdC.setPassengerPhone("0778887766");
        reservationService.lockSeats(holdC, null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + charter.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schC.getId(),
                        "eventType", "Failed Reassignment Attempt"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // Entire change rolled back: Schedule B remains charter, Schedule C remains public with hold
        lockedB = schedules.findById(schB.getId()).orElseThrow();
        assertTrue(lockedB.isCharter(), "Schedule B must remain charter when reassignment to C fails");
        Schedule lockedC = schedules.findById(schC.getId()).orElseThrow();
        assertFalse(lockedC.isCharter(), "Schedule C must remain public");

        // 4. Prevent two charters from owning the same schedule
        GroupBooking charter2 = new GroupBooking();
        charter2.setCustomerName("Second Group");
        charter2.setCustomerPhone("0773332211");
        charter2.setStartDate(depB);
        charter2.setEndDate(depB.plusHours(4));
        charter2.setPassengerCount(25);
        charter2.setTotalCost(BigDecimal.valueOf(55000.0));
        charter2.setDepositAmount(BigDecimal.valueOf(16500.0));
        charter2.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter2 = groupBookings.save(charter2);
        eerService.syncGroup(charter2);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + charter2.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schB.getId(),
                        "eventType", "Conflicting Second Group"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // 5. Cancellation safely releases Schedule B
        var cancelReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        cancelReq.setStatus("CANCELLED");
        groupBookingService.updateBookingStatus(charter.getId(), cancelReq);

        lockedB = schedules.findById(schB.getId()).orElseThrow();
        assertFalse(lockedB.isCharter(), "Schedule B must be released when charter is cancelled");
    }

    @Test
    void testConcurrentCompetingLostItemClaims() throws Exception {
        User supUser = createUser("STAFF", "Secret123!");
        eerService.profile(supUser);
        StaffProfile supStaff = staff.findByUserId(supUser.getId()).orElseThrow();
        supStaff.setStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        staff.save(supStaff);
        Cookie supCookie = login(supUser, "Secret123!");

        User claimantA = createUser("PASSENGER", "Secret123!");
        User claimantB = createUser("PASSENGER", "Secret123!");

        LostItem item = new LostItem();
        item.setItemDescription("Gold Ring lost near seat 12");
        item.setReportedByName("Finder John");
        item.setReportedByPhone("0771234567");
        item.setStatus(LostItemStatus.FOUND);
        item = lostItems.save(item);

        LostItemClaim claimA = new LostItemClaim();
        claimA.setItem(item);
        claimA.setClaimant(claimantA);
        claimA.setProofOfOwnership("Engraved initials JR");
        claimA.setClaimStatus("PENDING");
        claimA.setClaimDate(LocalDateTime.now());
        claimA = claims.save(claimA);

        LostItemClaim claimB = new LostItemClaim();
        claimB.setItem(item);
        claimB.setClaimant(claimantB);
        claimB.setProofOfOwnership("Purchase receipt from 2024");
        claimB.setClaimStatus("PENDING");
        claimB.setClaimDate(LocalDateTime.now());
        claimB = claims.save(claimB);

        final Integer claimAId = claimA.getId();
        final Integer claimBId = claimB.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger approvedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/claims/" + claimAId)
                        .cookie(supCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("status", "APPROVED"))))
                        .andReturn().getResponse();
                if (resp.getStatus() == 200) approvedCount.incrementAndGet();
                else if (resp.getStatus() == 400) rejectedCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/claims/" + claimBId)
                        .cookie(supCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("status", "APPROVED"))))
                        .andReturn().getResponse();
                if (resp.getStatus() == 200) approvedCount.incrementAndGet();
                else if (resp.getStatus() == 400) rejectedCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        startLatch.countDown();
        CompletableFuture.allOf(t1, t2).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one claim approved; second claim must be rejected with conflict
        assertEquals(1, approvedCount.get(), "Exactly one competing claim must be approved");
        assertEquals(1, rejectedCount.get(), "Competing claim must be rejected with HTTP 400 Bad Request");

        LostItem committedItem = lostItems.findById(item.getId()).orElseThrow();
        assertEquals(LostItemStatus.CLAIMED, committedItem.getStatus());
    }

    @Test
    void testConcurrentDuplicateManualCheckIn() throws Exception {
        Route route = new Route();
        route.setOrigin("CheckIn-Org");
        route.setDestination("CheckIn-Dst");
        route.setDistanceKm(BigDecimal.valueOf(50.0));
        route.setBaseFare(BigDecimal.valueOf(800.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = buses.save(new Bus("CHK-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);

        // Departure within 2 hours (open boarding window: 4 hours before departure)
        LocalDateTime dep = LocalDateTime.now().plusHours(2);
        Schedule sch = schedules.save(new Schedule(route, bus, null, dep, dep.plusHours(1), Schedule.ScheduleStatus.SCHEDULED));

        Reservation res = new Reservation();
        res.setSchedule(sch);
        res.setPassengerName("Checkin Passenger");
        res.setPassengerPhone("0773335577");
        res.setTotalFare(BigDecimal.valueOf(800.0));
        res.setStatus(Reservation.ReservationStatus.CONFIRMED);
        res.setCreatedAt(LocalDateTime.now());
        res = reservations.save(res);

        Ticket ticket = new Ticket();
        ticket.setReservation(res);
        ticket.setQrCode("TKT-CONC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setIssueDate(LocalDateTime.now());
        ticket.setSeatRange("12");
        ticket = tickets.save(ticket);

        User coordUser = createUser("STAFF", "Secret123!");
        eerService.profile(coordUser);
        StaffProfile coordStaff = staff.findByUserId(coordUser.getId()).orElseThrow();
        coordStaff.setStaffType("E_TICKETING_COORDINATOR");
        staff.save(coordStaff);
        Cookie coordCookie = login(coordUser, "Secret123!");

        final String qr = ticket.getQrCode();
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger checkInSuccess = new AtomicInteger(0);
        AtomicInteger checkInRejected = new AtomicInteger(0);

        CompletableFuture<Void> c1 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(post("/api/eer/tickets/check-in")
                        .cookie(coordCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("qrCode", qr))))
                        .andReturn().getResponse();
                if (resp.getStatus() == 200) checkInSuccess.incrementAndGet();
                else if (resp.getStatus() == 400) checkInRejected.incrementAndGet();
            } catch (Exception ignored) {}
        });

        CompletableFuture<Void> c2 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(post("/api/eer/tickets/check-in")
                        .cookie(coordCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("qrCode", qr))))
                        .andReturn().getResponse();
                if (resp.getStatus() == 200) checkInSuccess.incrementAndGet();
                else if (resp.getStatus() == 400) checkInRejected.incrementAndGet();
            } catch (Exception ignored) {}
        });

        startLatch.countDown();
        CompletableFuture.allOf(c1, c2).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one check-in must succeed, second must fail with HTTP 400
        assertEquals(1, checkInSuccess.get(), "Exactly one check-in request must succeed");
        assertEquals(1, checkInRejected.get(), "Duplicate concurrent check-in must be rejected with HTTP 400");

        Ticket committedTicket = tickets.findById(ticket.getId()).orElseThrow();
        assertNotNull(committedTicket.getCheckedInAt(), "Ticket checkedInAt timestamp must be recorded");
    }

    @Test
    void testConcurrentSearchMaterializationVsScheduleUpdateAndCharterAssignment() throws Exception {
        Route route = new Route();
        route.setOrigin("ConcSearch-Org");
        route.setDestination("ConcSearch-Dst");
        route.setDistanceKm(BigDecimal.valueOf(180.0));
        route.setBaseFare(BigDecimal.valueOf(1200.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus1 = buses.save(new Bus("CS1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 45, "AC", Bus.BusStatus.ACTIVE));
        Bus bus2 = buses.save(new Bus("CS2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 45, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus1);
        eerService.syncSeats(bus2);

        LocalDateTime baseDep = LocalDateTime.now().plusDays(1).withHour(7).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime baseArr = baseDep.plusHours(3);

        // Daily recurring schedule template
        Schedule template = new Schedule();
        template.setRoute(route);
        template.setBus(bus1);
        template.setDepartureTime(baseDep);
        template.setArrivalTime(baseArr);
        template.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(LocalDate.now().plusDays(30));
        template.setCharter(false);
        template = schedules.save(template);

        User opUser = createUser("STAFF", "OpPass123!");
        eerService.profile(opUser);
        StaffProfile opStaff = staff.findByUserId(opUser.getId()).orElseThrow();
        opStaff.setStaffType("OPERATIONS_MANAGER");
        staff.save(opStaff);
        Cookie opCookie = login(opUser, "OpPass123!");

        User custUser = createUser("PASSENGER", "CustPass123!");
        CustomerProfile cust = eerService.customer(custUser);
        cust.setFirstName("Charter");
        cust.setLastName("Seeker");
        cust.setAddress("Colombo");
        customers.save(cust);

        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Charter Seeker");
        charter.setCustomerPhone("0772228899");
        charter.setStartDate(baseDep);
        charter.setEndDate(baseArr);
        charter.setPassengerCount(35);
        charter.setTotalCost(BigDecimal.valueOf(70000.0));
        charter.setDepositAmount(BigDecimal.valueOf(21000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        final int fRouteId = route.getId();
        final int fTemplateId = template.getId();
        final int fCharterId = charter.getId();
        final int fCustId = cust.getId();
        final int fBus2Id = bus2.getId();
        final LocalDateTime fBaseDep = baseDep;
        final LocalDateTime fBaseArr = baseArr;
        final LocalDate targetDate = LocalDate.now().plusDays(2);

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger searchSuccess = new AtomicInteger(0);
        AtomicInteger updateSuccess = new AtomicInteger(0);
        AtomicInteger configureSuccess = new AtomicInteger(0);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> firstError = new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.atomic.AtomicReference<List<Schedule>> foundSchedules = new java.util.concurrent.atomic.AtomicReference<>(List.of());

        // Thread 1: Search & materialize recurring occurrences
        CompletableFuture<Void> tSearch = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                List<Schedule> found = scheduleService.searchSchedules("ConcSearch-Org", "ConcSearch-Dst", targetDate);
                assertNotNull(found);
                for (Schedule s : found) {
                    assertFalse(s.isCharter(), "Private charter must never be returned in public search results");
                    assertEquals(Schedule.ScheduleStatus.SCHEDULED, s.getStatus());
                    assertNotNull(s.getRoute(), "Occurrence must be associated with route");
                }
                foundSchedules.set(found);
                searchSuccess.incrementAndGet();
            } catch (Exception e) {
                firstError.compareAndSet(null, e);
                unexpectedErrors.incrementAndGet();
            }
        });

        // Thread 2: Concurrent schedule update (reassign bus or timing under lock)
        CompletableFuture<Void> tUpdate = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                ScheduleRequest sReq = new ScheduleRequest();
                sReq.setRouteId(fRouteId);
                sReq.setBusId(fBus2Id);
                sReq.setDepartureTime(fBaseDep);
                sReq.setArrivalTime(fBaseArr);
                sReq.setRepeatDaily(true);
                sReq.setRepeatUntil(LocalDate.now().plusDays(30));
                // Update schedule
                Schedule updated = scheduleService.updateSchedule(fTemplateId, sReq);
                if (updated != null) updateSuccess.incrementAndGet();
            } catch (Exception e) {
                // If validation rejected due to concurrent state change, it is acceptable, but 500/deadlock is not
                if (!e.getMessage().contains("already has dated departures") && !(e instanceof org.springframework.web.server.ResponseStatusException)) {
                    firstError.compareAndSet(null, e);
                    unexpectedErrors.incrementAndGet();
                }
            }
        });

        // Thread 3: Concurrent configureGroup via API
        CompletableFuture<Void> tConfigure = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + fCharterId)
                        .cookie(opCookie)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "customerId", fCustId,
                                "scheduleId", fTemplateId,
                                "eventType", "Concurrent Event"
                        ))))
                        .andReturn().getResponse();
                if (resp.getStatus() == 200) {
                    configureSuccess.incrementAndGet();
                } else if (resp.getStatus() != 400 && resp.getStatus() != 409) {
                    firstError.compareAndSet(null, new RuntimeException("Configure returned status: " + resp.getStatus()));
                    unexpectedErrors.incrementAndGet();
                }
            } catch (Exception e) {
                firstError.compareAndSet(null, e);
                unexpectedErrors.incrementAndGet();
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(tSearch, tUpdate, tConfigure).get(15, java.util.concurrent.TimeUnit.SECONDS);

        if (firstError.get() != null) {
            fail("Thread encountered unexpected error: " + firstError.get().getMessage(), firstError.get());
        }
        assertEquals(0, unexpectedErrors.get(), "No thread must encounter deadlocks, timeouts, or unexpected errors");
        assertTrue(searchSuccess.get() > 0, "Public search must complete successfully without deadlocking");

        // Verify committed database state integrity and absence of duplicate occurrences
        Schedule committedTemplate = schedules.findById(fTemplateId).orElseThrow();
        assertNotNull(committedTemplate.getBus(), "Template bus must be intact");
        assertNotEquals(Schedule.ScheduleStatus.CANCELLED, committedTemplate.getStatus());

        List<Schedule> dbOccurrences = schedules.findByRecurrenceParentId(fTemplateId);
        long distinctDepartures = dbOccurrences.stream().map(Schedule::getDepartureTime).distinct().count();
        assertEquals(dbOccurrences.size(), distinctDepartures, "Must have no duplicate recurring occurrences in the database");

        for (Schedule occ : dbOccurrences) {
            assertNotNull(occ.getBus(), "Materialized occurrence must have an assigned bus");
            assertEquals(Schedule.ScheduleStatus.SCHEDULED, occ.getStatus());
            assertFalse(occ.isCharter(), "Materialized occurrence must not be marked charter");
        }
    }

    @Test
    void testGroupApprovalWithMatchingDedicatedScheduleAndDepositLifecycle() throws Exception {
        Route route = new Route();
        route.setOrigin("Dedicated-Org");
        route.setDestination("Dedicated-Dst");
        route.setDistanceKm(BigDecimal.valueOf(150.0));
        route.setBaseFare(BigDecimal.valueOf(1000.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = buses.save(new Bus("DED-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus);

        LocalDateTime dep = LocalDateTime.now().plusDays(3).withHour(9).withMinute(0);
        LocalDateTime arr = dep.plusHours(4);

        Schedule dedicatedSchedule = new Schedule();
        dedicatedSchedule.setRoute(route);
        dedicatedSchedule.setBus(bus);
        dedicatedSchedule.setDepartureTime(dep);
        dedicatedSchedule.setArrivalTime(arr);
        dedicatedSchedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        dedicatedSchedule.setCharter(true); // marked as charter
        dedicatedSchedule = schedules.save(dedicatedSchedule);

        User opUser = createUser("STAFF", "OpPassDedicated!");
        eerService.profile(opUser);
        StaffProfile opStaff = staff.findByUserId(opUser.getId()).orElseThrow();
        opStaff.setStaffType("OPERATIONS_MANAGER");
        staff.save(opStaff);
        Cookie opCookie = login(opUser, "OpPassDedicated!");

        User custUser = createUser("PASSENGER", "CustPassDedicated!");
        CustomerProfile cust = eerService.customer(custUser);
        cust.setFirstName("Dedicated");
        cust.setLastName("Group");
        cust.setAddress("Kandy");
        customers.save(cust);

        // Group with this dedicated schedule linked in parent booking
        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Dedicated Group");
        charter.setCustomerPhone("0773334455");
        charter.setStartDate(dep);
        charter.setEndDate(arr);
        charter.setPassengerCount(30);
        charter.setTotalCost(BigDecimal.valueOf(60000.0));
        charter.setDepositAmount(BigDecimal.valueOf(18000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter.setAssignedBus(bus);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        // Link parent booking to dedicated schedule
        charter.getBooking().setSchedule(dedicatedSchedule);
        charter.getBooking().setCustomer(cust);
        bookings.save(charter.getBooking());
        charter = groupBookings.save(charter);

        // 1. Approval of pending group with its own matching dedicated schedule must SUCCEED
        var approveReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        approveReq.setStatus("APPROVED");
        approveReq.setAssignedBusId(bus.getId());

        GroupBookingResponse approvedResp = groupBookingService.updateBookingStatus(charter.getId(), approveReq);
        assertEquals("APPROVED", approvedResp.getStatus());

        // 2. Deposit payment processing works after approval
        Payment depositPay = new Payment();
        depositPay.setBooking(charter.getBooking());
        depositPay.setAmount(charter.getDepositAmount());
        depositPay.setPaymentMethod(Payment.PaymentMethod.CARD);
        depositPay.setStatus(Payment.PaymentStatus.SUCCESS);
        depositPay.setPaymentType("DEPOSIT");
        payments.save(depositPay);

        var depositStatusReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        depositStatusReq.setStatus("DEPOSIT_PAID");
        depositStatusReq.setAssignedBusId(bus.getId());

        GroupBookingResponse depositResp = groupBookingService.updateBookingStatus(charter.getId(), depositStatusReq);
        assertEquals("DEPOSIT_PAID", depositResp.getStatus());

        // 3. Proving another group's schedule CANNOT be excluded
        Schedule otherSchedule = new Schedule();
        otherSchedule.setRoute(route);
        otherSchedule.setBus(bus);
        otherSchedule.setDepartureTime(dep.plusDays(1));
        otherSchedule.setArrivalTime(arr.plusDays(1));
        otherSchedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        otherSchedule.setCharter(true);
        otherSchedule = schedules.save(otherSchedule);

        GroupBooking otherGroup = new GroupBooking();
        otherGroup.setCustomerName("Other Group");
        otherGroup.setCustomerPhone("0779998877");
        otherGroup.setStartDate(dep.plusDays(1));
        otherGroup.setEndDate(arr.plusDays(1));
        otherGroup.setPassengerCount(25);
        otherGroup.setTotalCost(BigDecimal.valueOf(50000.0));
        otherGroup.setDepositAmount(BigDecimal.valueOf(15000.0));
        otherGroup.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        otherGroup.setAssignedBus(bus);
        otherGroup = groupBookings.save(otherGroup);
        eerService.syncGroup(otherGroup);
        otherGroup.getBooking().setSchedule(otherSchedule);
        bookings.save(otherGroup.getBooking());
        otherGroup = groupBookings.save(otherGroup);

        final int fCharterId = charter.getId();
        final int fOtherScheduleId = otherSchedule.getId();

        // Attempting to exclude other group's schedule for this group must be rejected
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> eerService.validateBusAssignmentForCharter(bus.getId(), fCharterId, fOtherScheduleId, dep, arr, 30)
        );
        assertTrue(ex.getMessage().contains("Cannot exclude schedule"), "Cannot exclude another charter's schedule");

        // 4. Invalid changes roll back without corrupting booking, schedule, payment, or bus state
        var invalidStatusReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        invalidStatusReq.setStatus("INVALID_STATUS");
        assertThrows(RuntimeException.class, () -> groupBookingService.updateBookingStatus(fCharterId, invalidStatusReq));

        GroupBooking reloadedCharter = groupBookings.findById(fCharterId).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.DEPOSIT_PAID, reloadedCharter.getStatus());
        assertEquals(bus.getId(), reloadedCharter.getAssignedBus().getId());
        assertEquals(dedicatedSchedule.getId(), reloadedCharter.getBooking().getSchedule().getId());
    }

    @Test
    void testConfigureGroupRejectsCancelledAndCompletedGroupsWithoutSideEffects() throws Exception {
        Route route = new Route();
        route.setOrigin("Term-Org");
        route.setDestination("Term-Dst");
        route.setDistanceKm(BigDecimal.valueOf(100.0));
        route.setBaseFare(BigDecimal.valueOf(900.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = buses.save(new Bus("TMA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busB = buses.save(new Bus("TMB-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(busA);
        eerService.syncSeats(busB);

        LocalDateTime dep = LocalDateTime.now().plusDays(4).withHour(8).withMinute(0);
        LocalDateTime arr = dep.plusHours(3);

        Schedule schPublic = new Schedule();
        schPublic.setRoute(route);
        schPublic.setBus(busB);
        schPublic.setDepartureTime(dep);
        schPublic.setArrivalTime(arr);
        schPublic.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schPublic.setCharter(false); // public schedule
        schPublic = schedules.save(schPublic);

        User opUser = createUser("STAFF", "OpPassTerm!");
        eerService.profile(opUser);
        StaffProfile opStaff = staff.findByUserId(opUser.getId()).orElseThrow();
        opStaff.setStaffType("OPERATIONS_MANAGER");
        staff.save(opStaff);
        Cookie opCookie = login(opUser, "OpPassTerm!");

        User custUser = createUser("PASSENGER", "CustPassTerm!");
        CustomerProfile cust = eerService.customer(custUser);
        cust.setFirstName("Terminal");
        cust.setLastName("User");
        cust.setAddress("Galle");
        customers.save(cust);

        // Case A: CANCELLED Group
        GroupBooking cancelledGroup = new GroupBooking();
        cancelledGroup.setCustomerName("Cancelled Group");
        cancelledGroup.setCustomerPhone("0771122334");
        cancelledGroup.setStartDate(dep);
        cancelledGroup.setEndDate(arr);
        cancelledGroup.setPassengerCount(25);
        cancelledGroup.setTotalCost(BigDecimal.valueOf(45000.0));
        cancelledGroup.setDepositAmount(BigDecimal.valueOf(13500.0));
        cancelledGroup.setStatus(GroupBooking.GroupBookingStatus.CANCELLED);
        cancelledGroup.setAssignedBus(busA);
        cancelledGroup = groupBookings.save(cancelledGroup);
        eerService.syncGroup(cancelledGroup);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + cancelledGroup.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schPublic.getId(),
                        "eventType", "Attempt on Cancelled"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // Assert no side effects on CANCELLED group or target schedule
        Schedule reloadedPublic = schedules.findById(schPublic.getId()).orElseThrow();
        assertFalse(reloadedPublic.isCharter(), "Target schedule MUST remain public (isCharter=false)");
        GroupBooking reloadedCancelled = groupBookings.findById(cancelledGroup.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.CANCELLED, reloadedCancelled.getStatus());
        assertEquals(busA.getId(), reloadedCancelled.getAssignedBus().getId(), "Assigned bus must not change");

        // Case B: COMPLETED Group
        GroupBooking completedGroup = new GroupBooking();
        completedGroup.setCustomerName("Completed Group");
        completedGroup.setCustomerPhone("0772233445");
        completedGroup.setStartDate(dep);
        completedGroup.setEndDate(arr);
        completedGroup.setPassengerCount(25);
        completedGroup.setTotalCost(BigDecimal.valueOf(45000.0));
        completedGroup.setDepositAmount(BigDecimal.valueOf(13500.0));
        completedGroup.setStatus(GroupBooking.GroupBookingStatus.COMPLETED);
        completedGroup.setAssignedBus(busA);
        completedGroup = groupBookings.save(completedGroup);
        eerService.syncGroup(completedGroup);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + completedGroup.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "customerId", cust.getId(),
                        "scheduleId", schPublic.getId(),
                        "eventType", "Attempt on Completed"
                ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        // Assert no side effects on COMPLETED group or target schedule
        reloadedPublic = schedules.findById(schPublic.getId()).orElseThrow();
        assertFalse(reloadedPublic.isCharter(), "Target schedule MUST remain public (isCharter=false)");
        GroupBooking reloadedCompleted = groupBookings.findById(completedGroup.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.COMPLETED, reloadedCompleted.getStatus());
        assertEquals(busA.getId(), reloadedCompleted.getAssignedBus().getId(), "Assigned bus must not change");
    }

    @Test
    void testCancellationRacingWithReassignmentSafety() throws Exception {
        Route route = new Route();
        route.setOrigin("Race-Org");
        route.setDestination("Race-Dst");
        route.setDistanceKm(BigDecimal.valueOf(110.0));
        route.setBaseFare(BigDecimal.valueOf(950.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        User opUser = createUser("STAFF", "OpPassRace!");
        eerService.profile(opUser);
        StaffProfile opStaff = staff.findByUserId(opUser.getId()).orElseThrow();
        opStaff.setStaffType("OPERATIONS_MANAGER");
        staff.save(opStaff);
        Cookie opCookie = login(opUser, "OpPassRace!");

        User custUser = createUser("PASSENGER", "CustPassRace!");
        CustomerProfile cust = eerService.customer(custUser);
        cust.setFirstName("Racer");
        cust.setLastName("User");
        cust.setAddress("Colombo");
        customers.save(cust);

        // -------------------------------------------------------------
        // Ordering A (Deterministic): Reassignment succeeds first -> Cancellation runs on reassigned group
        // -------------------------------------------------------------
        Bus busA1 = buses.save(new Bus("ORD-A1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busA2 = buses.save(new Bus("ORD-A2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busA3 = buses.save(new Bus("ORD-A3-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(busA1); eerService.syncSeats(busA2); eerService.syncSeats(busA3);

        LocalDateTime depA1 = LocalDateTime.now().plusDays(5).withHour(7).withMinute(0);
        LocalDateTime depA2 = LocalDateTime.now().plusDays(5).withHour(11).withMinute(0);
        LocalDateTime depA3 = LocalDateTime.now().plusDays(5).withHour(15).withMinute(0);

        Schedule schAOld = schedules.save(new Schedule(route, busA1, null, depA1, depA1.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schAOld.setCharter(true); schAOld = schedules.save(schAOld);

        Schedule schANew = schedules.save(new Schedule(route, busA2, null, depA2, depA2.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schANew.setCharter(false); schANew = schedules.save(schANew);

        // Third schedule belonging to another active charter that MUST remain protected
        Schedule schAProtected = schedules.save(new Schedule(route, busA3, null, depA3, depA3.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schAProtected.setCharter(true); schAProtected = schedules.save(schAProtected);

        GroupBooking otherActiveA = new GroupBooking();
        otherActiveA.setCustomerName("Other Active Group A");
        otherActiveA.setCustomerPhone("0771112222");
        otherActiveA.setStartDate(depA3); otherActiveA.setEndDate(depA3.plusHours(3));
        otherActiveA.setPassengerCount(25); otherActiveA.setTotalCost(BigDecimal.valueOf(50000.0));
        otherActiveA.setDepositAmount(BigDecimal.valueOf(15000.0));
        otherActiveA.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        otherActiveA.setAssignedBus(busA3);
        otherActiveA = groupBookings.save(otherActiveA);
        eerService.syncGroup(otherActiveA);
        otherActiveA.getBooking().setSchedule(schAProtected);
        bookings.save(otherActiveA.getBooking());

        GroupBooking groupA = new GroupBooking();
        groupA.setCustomerName("Racing Group A"); groupA.setCustomerPhone("0775566771");
        groupA.setStartDate(depA1); groupA.setEndDate(depA1.plusHours(3));
        groupA.setPassengerCount(30); groupA.setTotalCost(BigDecimal.valueOf(50000.0));
        groupA.setDepositAmount(BigDecimal.valueOf(15000.0));
        groupA.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        groupA.setAssignedBus(busA1);
        groupA = groupBookings.save(groupA);
        eerService.syncGroup(groupA);
        groupA.getBooking().setSchedule(schAOld);
        bookings.save(groupA.getBooking());
        groupA = groupBookings.save(groupA);

        // Step 1: Reassign to schANew
        var reassignRespA = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + groupA.getId())
                .cookie(opCookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("customerId", cust.getId(), "scheduleId", schANew.getId(), "eventType", "Reassigned A"))))
                .andReturn().getResponse();
        assertEquals(200, reassignRespA.getStatus(), "Deterministic reassignment must succeed");

        Schedule postReassignOld = schedules.findById(schAOld.getId()).orElseThrow();
        Schedule postReassignNew = schedules.findById(schANew.getId()).orElseThrow();
        assertFalse(postReassignOld.isCharter(), "Old schedule must be released after reassignment");
        assertTrue(postReassignNew.isCharter(), "New schedule must be marked charter after reassignment");

        // Step 2: Cancel group
        var cancelReqA = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        cancelReqA.setStatus("CANCELLED");
        var cancelResultA = groupBookingService.updateBookingStatus(groupA.getId(), cancelReqA);
        assertEquals("CANCELLED", cancelResultA.getStatus(), "Cancellation must succeed");

        GroupBooking finalGroupA = groupBookings.findById(groupA.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.CANCELLED, finalGroupA.getStatus());
        assertEquals("CANCELLED", finalGroupA.getBooking().getBookingStatus());
        assertFalse(schedules.findById(schAOld.getId()).orElseThrow().isCharter(), "Old schedule must not remain charter");
        assertFalse(schedules.findById(schANew.getId()).orElseThrow().isCharter(), "Newly assigned schedule must not remain charter");
        assertTrue(schedules.findById(schAProtected.getId()).orElseThrow().isCharter(), "Other active charter must remain protected");

        // -------------------------------------------------------------
        // Ordering B (Deterministic): Cancellation runs first -> Reassignment is rejected with terminal-state check
        // -------------------------------------------------------------
        Bus busB1 = buses.save(new Bus("ORD-B1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busB2 = buses.save(new Bus("ORD-B2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busB3 = buses.save(new Bus("ORD-B3-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(busB1); eerService.syncSeats(busB2); eerService.syncSeats(busB3);

        LocalDateTime depB1 = LocalDateTime.now().plusDays(6).withHour(7).withMinute(0);
        LocalDateTime depB2 = LocalDateTime.now().plusDays(6).withHour(11).withMinute(0);
        LocalDateTime depB3 = LocalDateTime.now().plusDays(6).withHour(15).withMinute(0);

        Schedule schBOld = schedules.save(new Schedule(route, busB1, null, depB1, depB1.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schBOld.setCharter(true); schBOld = schedules.save(schBOld);

        Schedule schBNew = schedules.save(new Schedule(route, busB2, null, depB2, depB2.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schBNew.setCharter(false); schBNew = schedules.save(schBNew);

        Schedule schBProtected = schedules.save(new Schedule(route, busB3, null, depB3, depB3.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schBProtected.setCharter(true); schBProtected = schedules.save(schBProtected);

        GroupBooking otherActiveB = new GroupBooking();
        otherActiveB.setCustomerName("Other Active Group B"); otherActiveB.setCustomerPhone("0772223333");
        otherActiveB.setStartDate(depB3); otherActiveB.setEndDate(depB3.plusHours(3));
        otherActiveB.setPassengerCount(25); otherActiveB.setTotalCost(BigDecimal.valueOf(50000.0));
        otherActiveB.setDepositAmount(BigDecimal.valueOf(15000.0));
        otherActiveB.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        otherActiveB.setAssignedBus(busB3);
        otherActiveB = groupBookings.save(otherActiveB);
        eerService.syncGroup(otherActiveB);
        otherActiveB.getBooking().setSchedule(schBProtected);
        bookings.save(otherActiveB.getBooking());

        GroupBooking groupB = new GroupBooking();
        groupB.setCustomerName("Racing Group B"); groupB.setCustomerPhone("0775566772");
        groupB.setStartDate(depB1); groupB.setEndDate(depB1.plusHours(3));
        groupB.setPassengerCount(30); groupB.setTotalCost(BigDecimal.valueOf(50000.0));
        groupB.setDepositAmount(BigDecimal.valueOf(15000.0));
        groupB.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        groupB.setAssignedBus(busB1);
        groupB = groupBookings.save(groupB);
        eerService.syncGroup(groupB);
        groupB.getBooking().setSchedule(schBOld);
        bookings.save(groupB.getBooking());
        groupB = groupBookings.save(groupB);

        // Step 1: Cancel group
        var cancelReqB = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
        cancelReqB.setStatus("CANCELLED");
        var cancelResultB = groupBookingService.updateBookingStatus(groupB.getId(), cancelReqB);
        assertEquals("CANCELLED", cancelResultB.getStatus());
        assertFalse(schedules.findById(schBOld.getId()).orElseThrow().isCharter(), "Old schedule must be released on cancellation");

        // Step 2: Attempt reassignment on cancelled group -> MUST fail with specific error
        var reassignRespB = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + groupB.getId())
                .cookie(opCookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("customerId", cust.getId(), "scheduleId", schBNew.getId(), "eventType", "Reassigned B"))))
                .andReturn().getResponse();
        assertEquals(400, reassignRespB.getStatus(), "Reassigning CANCELLED group must return 400 Bad Request");
        String errorMsgB = reassignRespB.getContentAsString() != null ? reassignRespB.getContentAsString() : "";
        if (reassignRespB.getErrorMessage() != null) errorMsgB += " " + reassignRespB.getErrorMessage();
        assertTrue(errorMsgB.contains("Cannot configure a CANCELLED group booking."),
                "Must reject with specific terminal-state business error message, got: " + errorMsgB);

        GroupBooking finalGroupB = groupBookings.findById(groupB.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.CANCELLED, finalGroupB.getStatus());
        assertEquals("CANCELLED", finalGroupB.getBooking().getBookingStatus());
        assertFalse(schedules.findById(schBOld.getId()).orElseThrow().isCharter());
        assertFalse(schedules.findById(schBNew.getId()).orElseThrow().isCharter(), "New schedule must not be converted to charter");
        assertTrue(schedules.findById(schBProtected.getId()).orElseThrow().isCharter(), "Protected schedule must remain protected");

        // -------------------------------------------------------------
        // Ordering C: Concurrent Racing with worker exception propagation
        // -------------------------------------------------------------
        Bus bus1 = buses.save(new Bus("RC1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus bus2 = buses.save(new Bus("RC2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus bus3 = buses.save(new Bus("RC3-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus1); eerService.syncSeats(bus2); eerService.syncSeats(bus3);

        LocalDateTime dep1 = LocalDateTime.now().plusDays(7).withHour(7).withMinute(0);
        LocalDateTime dep2 = LocalDateTime.now().plusDays(7).withHour(11).withMinute(0);
        LocalDateTime dep3 = LocalDateTime.now().plusDays(7).withHour(15).withMinute(0);

        Schedule schOld = schedules.save(new Schedule(route, bus1, null, dep1, dep1.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schOld.setCharter(true); schOld = schedules.save(schOld);

        Schedule schNew = schedules.save(new Schedule(route, bus2, null, dep2, dep2.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schNew.setCharter(false); schNew = schedules.save(schNew);

        Schedule schProtected = schedules.save(new Schedule(route, bus3, null, dep3, dep3.plusHours(3), Schedule.ScheduleStatus.SCHEDULED));
        schProtected.setCharter(true); schProtected = schedules.save(schProtected);

        GroupBooking otherActive = new GroupBooking();
        otherActive.setCustomerName("Other Active Group"); otherActive.setCustomerPhone("0773334444");
        otherActive.setStartDate(dep3); otherActive.setEndDate(dep3.plusHours(3));
        otherActive.setPassengerCount(25); otherActive.setTotalCost(BigDecimal.valueOf(50000.0));
        otherActive.setDepositAmount(BigDecimal.valueOf(15000.0));
        otherActive.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        otherActive.setAssignedBus(bus3);
        otherActive = groupBookings.save(otherActive);
        eerService.syncGroup(otherActive);
        otherActive.getBooking().setSchedule(schProtected);
        bookings.save(otherActive.getBooking());

        GroupBooking racingGroup = new GroupBooking();
        racingGroup.setCustomerName("Racing Group"); racingGroup.setCustomerPhone("0775566778");
        racingGroup.setStartDate(dep1); racingGroup.setEndDate(dep1.plusHours(3));
        racingGroup.setPassengerCount(30); racingGroup.setTotalCost(BigDecimal.valueOf(50000.0));
        racingGroup.setDepositAmount(BigDecimal.valueOf(15000.0));
        racingGroup.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        racingGroup.setAssignedBus(bus1);
        racingGroup = groupBookings.save(racingGroup);
        eerService.syncGroup(racingGroup);
        racingGroup.getBooking().setSchedule(schOld);
        bookings.save(racingGroup.getBooking());
        racingGroup = groupBookings.save(racingGroup);

        final int fGroupId = racingGroup.getId();
        final int fNewSchId = schNew.getId();
        final int fOldSchId = schOld.getId();
        final int fProtectedSchId = schProtected.getId();
        final int fCustId = cust.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger reassignmentStatus = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> reassignError = new java.util.concurrent.atomic.AtomicReference<>("");
        AtomicBoolean cancelSuccess = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> workerException = new java.util.concurrent.atomic.AtomicReference<>(null);

        // Thread 1: Reassign to schNew
        CompletableFuture<Void> tReassign = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + fGroupId)
                        .cookie(opCookie).contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "customerId", fCustId,
                                "scheduleId", fNewSchId,
                                "eventType", "Reassigned Event"
                        ))))
                        .andReturn().getResponse();
                reassignmentStatus.set(resp.getStatus());
                String msg = resp.getContentAsString() != null ? resp.getContentAsString() : "";
                if (resp.getErrorMessage() != null) msg += " " + resp.getErrorMessage();
                reassignError.set(msg);
            } catch (Throwable t) {
                workerException.compareAndSet(null, t);
            }
        });

        // Thread 2: Concurrent cancel
        CompletableFuture<Void> tCancel = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                var cancelReq = new com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest();
                cancelReq.setStatus("CANCELLED");
                var res = groupBookingService.updateBookingStatus(fGroupId, cancelReq);
                if ("CANCELLED".equals(res.getStatus())) {
                    cancelSuccess.set(true);
                }
            } catch (Throwable t) {
                workerException.compareAndSet(null, t);
            }
        });

        startLatch.countDown();
        CompletableFuture.allOf(tReassign, tCancel).get(15, java.util.concurrent.TimeUnit.SECONDS);

        // Assert worker exceptions propagate and fail the test if any occurred
        if (workerException.get() != null) {
            fail("Worker thread threw unexpected exception: " + workerException.get().getMessage(), workerException.get());
        }

        // Cancellation must succeed for this valid fixture
        assertTrue(cancelSuccess.get(), "Cancellation must succeed for this valid fixture");

        // Reassignment may succeed before cancellation (200), or receive expected terminal-state rejection after cancellation (400)
        int reassignCode = reassignmentStatus.get();
        if (reassignCode == 400) {
            assertTrue(reassignError.get().contains("Cannot configure a CANCELLED group booking."),
                    "400 response must assert specific business error 'Cannot configure a CANCELLED group booking.', got: " + reassignError.get());
        } else if (reassignCode != 200) {
            fail("Reassignment returned unexpected status code: " + reassignCode + ", message: " + reassignError.get());
        }

        // The committed group must end CANCELLED
        GroupBooking finalGroup = groupBookings.findById(fGroupId).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.CANCELLED, finalGroup.getStatus(), "Committed group must end CANCELLED");
        assertEquals("CANCELLED", finalGroup.getBooking().getBookingStatus(), "Parent booking status must be CANCELLED");

        // Neither the old nor the newly assigned schedule remains private on behalf of the cancelled group
        Schedule finalOldSch = schedules.findById(fOldSchId).orElseThrow();
        Schedule finalNewSch = schedules.findById(fNewSchId).orElseThrow();
        assertFalse(finalOldSch.isCharter(), "Old schedule must not remain private charter");
        assertFalse(finalNewSch.isCharter(), "New schedule must not remain private charter");

        // Schedules belonging to another active charter remain protected
        Schedule finalProtected = schedules.findById(fProtectedSchId).orElseThrow();
        assertTrue(finalProtected.isCharter(), "Other active charter schedule must remain private charter");
    }

    @Test
    void testMaterializationWithBusAssignmentChangingBetweenDiscoveryAndLockAcquisition() throws Exception {
        Route route = new Route();
        route.setOrigin("LockFail-Org");
        route.setDestination("LockFail-Dst");
        route.setDistanceKm(BigDecimal.valueOf(120.0));
        route.setBaseFare(BigDecimal.valueOf(1100.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus1 = buses.save(new Bus("LF1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus bus2 = buses.save(new Bus("LF2-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus bus3 = buses.save(new Bus("LF3-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(bus1); eerService.syncSeats(bus2); eerService.syncSeats(bus3);

        LocalDateTime dep1 = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0);
        LocalDateTime dep2 = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDate targetDate = LocalDate.now().plusDays(2);

        // Multiple candidate templates on multiple bus IDs
        Schedule template1 = new Schedule(route, bus1, null, dep1, dep1.plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        template1.setRepeatDaily(true); template1.setRepeatUntil(LocalDate.now().plusDays(30));
        template1 = schedules.save(template1);

        Schedule template2 = new Schedule(route, bus2, null, dep2, dep2.plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        template2.setRepeatDaily(true); template2.setRepeatUntil(LocalDate.now().plusDays(30));
        template2 = schedules.save(template2);

        final int fTemplate1Id = template1.getId();
        final int fBus3Id = bus3.getId();

        // Hook: After candidate discovery (Template 1 with Bus 1, Template 2 with Bus 2), change Template 1's bus to Bus 3
        AtomicBoolean hookExecuted = new AtomicBoolean(false);
        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            if (hookExecuted.compareAndSet(false, true)) {
                // Change Template 1's bus to Bus 3 in an isolated new transaction
                org.springframework.transaction.support.TransactionTemplate tx =
                        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.execute(status -> {
                    Schedule s = schedules.findById(fTemplate1Id).orElseThrow();
                    Bus b3 = buses.findById(fBus3Id).orElseThrow();
                    s.setBus(b3);
                    schedules.save(s);
                    return null;
                });
            }
        });

        try {
            // Exercise real public search entrypoint
            List<Schedule> results = scheduleService.searchSchedules("LockFail-Org", "LockFail-Dst", targetDate);
            assertNotNull(results);
            assertTrue(hookExecuted.get(), "Synchronization hook must have executed");

            // Verify retry rediscovery succeeded and occurrences were materialized
            List<Schedule> occurrencesT1 = schedules.findByRecurrenceParentId(template1.getId());
            List<Schedule> occurrencesT2 = schedules.findByRecurrenceParentId(template2.getId());

            assertEquals(1, occurrencesT1.size(), "Template 1 must have exactly 1 occurrence on targetDate (no duplicates)");
            assertEquals(1, occurrencesT2.size(), "Template 2 must have exactly 1 occurrence on targetDate (no duplicates)");

            // Template 1's occurrence must have Bus 3 (the updated bus)
            assertEquals(bus3.getId(), occurrencesT1.get(0).getBus().getId(), "Occurrence must reflect the newly assigned bus");
            assertEquals(bus2.getId(), occurrencesT2.get(0).getBus().getId(), "Occurrence must reflect Template 2's bus");
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testMaterializationRetryExhaustionRollsBackCleanlyWithoutPartialChanges() {
        Route route = new Route();
        route.setOrigin("RetryExh-Org");
        route.setDestination("RetryExh-Dst");
        route.setDistanceKm(BigDecimal.valueOf(130.0));
        route.setBaseFare(BigDecimal.valueOf(1200.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = buses.save(new Bus("RXA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busB = buses.save(new Bus("RXB-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        Bus busC = buses.save(new Bus("RXC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 40, "AC", Bus.BusStatus.ACTIVE));
        eerService.syncSeats(busA); eerService.syncSeats(busB); eerService.syncSeats(busC);

        LocalDateTime dep = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDate targetDate = LocalDate.now().plusDays(3);

        Schedule template = new Schedule(route, busA, null, dep, dep.plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true); template.setRepeatUntil(LocalDate.now().plusDays(30));
        template = schedules.save(template);

        final int fTemplateId = template.getId();
        final int fBusBId = busB.getId();
        final int fBusCId = busC.getId();

        AtomicInteger hookInvocations = new AtomicInteger(0);
        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            int count = hookInvocations.incrementAndGet();
            int nextBusId = (count % 2 == 1) ? fBusBId : fBusCId;
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                Schedule s = schedules.findById(fTemplateId).orElseThrow();
                Bus b = buses.findById(nextBusId).orElseThrow();
                s.setBus(b);
                schedules.save(s);
                return null;
            });
        });

        try {
            org.springframework.web.server.ResponseStatusException ex = assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> scheduleService.searchSchedules("RetryExh-Org", "RetryExh-Dst", targetDate)
            );
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode(), "Must throw 409 CONFLICT on retry exhaustion");

            // Bounded retry exhaustion with no partial database changes
            List<Schedule> occurrences = schedules.findByRecurrenceParentId(fTemplateId);
            assertTrue(occurrences.isEmpty(), "No partial database changes: occurrences must be empty upon retry exhaustion rollback");
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testMaterializationWithOuterTransactionAndRetrySucceedsWithoutRollbackException() {
        // Calling ScheduleService from an active outer transaction must NOT fail with UnexpectedRollbackException
        // when an AssociationChangedRetryException triggers an internal retry.
        Route route = new Route();
        route.setOrigin("OuterTx-Org");
        route.setDestination("OuterTx-Dst");
        route.setDistanceKm(BigDecimal.valueOf(120.0));
        route.setBaseFare(BigDecimal.valueOf(800.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = new Bus();
        busA.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busA.setBusType("STANDARD");
        busA.setCapacity(40);
        busA.setStatus(Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);

        Bus busB = new Bus();
        busB.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busB.setBusType("LUXURY");
        busB.setCapacity(45);
        busB.setStatus(Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);

        LocalDate targetDate = LocalDate.now().plusDays(2);
        Schedule template = new Schedule();
        template.setRoute(route);
        template.setBus(busA);
        template.setDepartureTime(LocalDate.now().atTime(10, 0));
        template.setArrivalTime(LocalDate.now().atTime(12, 30));
        template.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(LocalDate.now().plusDays(10));
        template = schedules.save(template);

        final int fTemplateId = template.getId();
        final int fBusBId = busB.getId();
        AtomicInteger hookCount = new AtomicInteger(0);

        // Hook switches bus to busB on first attempt, causing AssociationChangedRetryException
        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            if (hookCount.incrementAndGet() == 1) {
                org.springframework.transaction.support.TransactionTemplate tx =
                        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.execute(status -> {
                    Schedule s = schedules.findById(fTemplateId).orElseThrow();
                    Bus b = buses.findById(fBusBId).orElseThrow();
                    s.setBus(b);
                    schedules.save(s);
                    return null;
                });
            }
        });

        try {
            // Caller executes within an active outer transaction
            org.springframework.transaction.support.TransactionTemplate outerTx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);

            List<Schedule> results = outerTx.execute(outerStatus ->
                    scheduleService.searchSchedules("OuterTx-Org", "OuterTx-Dst", targetDate)
            );

            // Assert: Outer transaction must commit cleanly without UnexpectedRollbackException
            assertNotNull(results, "Search must return results");
            assertFalse(results.isEmpty(), "Must have materialized occurrence");

            List<Schedule> occurrences = schedules.findByRecurrenceParentId(fTemplateId);
            assertEquals(1, occurrences.size(), "Exactly 1 occurrence must be materialized; no duplicates");
            assertEquals(fBusBId, occurrences.get(0).getBus().getId(), "Occurrence must use the updated bus discovered on retry");
            assertEquals(Schedule.ScheduleStatus.SCHEDULED, occurrences.get(0).getStatus());
            assertFalse(occurrences.get(0).isCharter());
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testUpdateScheduleWithOuterTransactionAndRetrySucceeds() {
        Route route = new Route();
        route.setOrigin("OuterUpd-Org");
        route.setDestination("OuterUpd-Dst");
        route.setDistanceKm(BigDecimal.valueOf(100.0));
        route.setBaseFare(BigDecimal.valueOf(700.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = new Bus();
        busA.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busA.setBusType("STANDARD");
        busA.setCapacity(40);
        busA.setStatus(Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);

        Bus busB = new Bus();
        busB.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busB.setBusType("LUXURY");
        busB.setCapacity(45);
        busB.setStatus(Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);

        Bus busC = new Bus();
        busC.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busC.setBusType("SUPER_LUXURY");
        busC.setCapacity(50);
        busC.setStatus(Bus.BusStatus.ACTIVE);
        busC = buses.save(busC);
        eerService.syncSeats(busC);

        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setBus(busA);
        schedule.setDepartureTime(LocalDate.now().plusDays(5).atTime(8, 0));
        schedule.setArrivalTime(LocalDate.now().plusDays(5).atTime(11, 0));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule.setRepeatDaily(false);
        schedule = schedules.save(schedule);

        final int fScheduleId = schedule.getId();
        final int fRouteId = route.getId();
        final int fBusAId = busA.getId();
        final int fBusBId = busB.getId();
        final int fBusCId = busC.getId();
        final LocalDateTime targetDep = LocalDate.now().plusDays(5).atTime(9, 0);
        final LocalDateTime targetArr = LocalDate.now().plusDays(5).atTime(12, 0);

        // Update request: change bus from current to busB
        ScheduleRequest req = new ScheduleRequest();
        req.setRouteId(fRouteId);
        req.setBusId(fBusBId);
        req.setDepartureTime(targetDep);
        req.setArrivalTime(targetArr);
        req.setRepeatDaily(false);

        // Synchronization mechanism:
        // On attempt 1, after candidate discovery of lock set {A, B} but BEFORE acquiring locks,
        // concurrently switch the schedule to bus C in a separate committed transaction.
        // C is outside {A, B}, which forces AssociationChangedRetryException.
        // On attempt 2, the hook does not intervene, allowing discovery of {C, B}, acquiring locks
        // in canonical order, and completing the update to bus B.
        AtomicInteger attemptsSeen = new AtomicInteger(0);
        AtomicBoolean retryTriggered = new AtomicBoolean(false);

        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            int attempt = attemptsSeen.incrementAndGet();
            if (attempt == 1) {
                retryTriggered.set(true);
                org.springframework.transaction.support.TransactionTemplate switchTx =
                        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                switchTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                switchTx.execute(status -> {
                    Schedule s = schedules.findById(fScheduleId).orElseThrow();
                    Bus c = buses.findById(fBusCId).orElseThrow();
                    s.setBus(c);
                    schedules.save(s);
                    return null;
                });
            }
        });

        try {
            // Caller invokes updateSchedule from an active outer transaction using committed fixtures
            org.springframework.transaction.support.TransactionTemplate outerTx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);

            Schedule updated = outerTx.execute(outerStatus -> {
                assertFalse(outerStatus.isRollbackOnly(), "Outer status must start clean");
                Schedule res = scheduleService.updateSchedule(fScheduleId, req);
                assertFalse(outerStatus.isRollbackOnly(), "Outer status must remain clean after inner retry");
                return res;
            });

            assertNotNull(updated);
            assertTrue(attemptsSeen.get() >= 2, "At least two attempts must occur, actual: " + attemptsSeen.get());
            assertTrue(retryTriggered.get(), "The intended association-change retry branch must be triggered");

            // Final persisted state must match the update request
            Schedule reloaded = schedules.findById(fScheduleId).orElseThrow();
            assertEquals(fBusBId, reloaded.getBus().getId(), "Committed schedule bus must be bus B");
            assertEquals(targetDep, reloaded.getDepartureTime(), "Departure time must match request");
            assertEquals(targetArr, reloaded.getArrivalTime(), "Arrival time must match request");
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testUpdateScheduleRetryExhaustionRollsBackCleanlyWithoutPartialWrites() {
        Route route = new Route();
        route.setOrigin("UpdExh-Org");
        route.setDestination("UpdExh-Dst");
        route.setDistanceKm(BigDecimal.valueOf(100.0));
        route.setBaseFare(BigDecimal.valueOf(700.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = new Bus();
        busA.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busA.setBusType("STANDARD");
        busA.setCapacity(40);
        busA.setStatus(Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);

        Bus busB = new Bus();
        busB.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busB.setBusType("LUXURY");
        busB.setCapacity(45);
        busB.setStatus(Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);

        Bus busC = new Bus();
        busC.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busC.setBusType("SUPER_LUXURY");
        busC.setCapacity(50);
        busC.setStatus(Bus.BusStatus.ACTIVE);
        busC = buses.save(busC);
        eerService.syncSeats(busC);

        Bus busD = new Bus();
        busD.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busD.setBusType("EXPRESS");
        busD.setCapacity(55);
        busD.setStatus(Bus.BusStatus.ACTIVE);
        busD = buses.save(busD);
        eerService.syncSeats(busD);

        final LocalDateTime initialDep = LocalDate.now().plusDays(6).atTime(8, 0);
        final LocalDateTime initialArr = LocalDate.now().plusDays(6).atTime(11, 0);

        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setBus(busA);
        schedule.setDepartureTime(initialDep);
        schedule.setArrivalTime(initialArr);
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule.setRepeatDaily(false);
        schedule = schedules.save(schedule);

        final int fScheduleId = schedule.getId();
        final int fRouteId = route.getId();
        final int fBusBId = busB.getId();
        final int fBusCId = busC.getId();
        final int fBusDId = busD.getId();

        // Update request wants to set departure to 10:00 and bus to busB
        final LocalDateTime requestedDep = LocalDate.now().plusDays(6).atTime(10, 0);
        final LocalDateTime requestedArr = LocalDate.now().plusDays(6).atTime(13, 0);
        ScheduleRequest req = new ScheduleRequest();
        req.setRouteId(fRouteId);
        req.setBusId(fBusBId);
        req.setDepartureTime(requestedDep);
        req.setArrivalTime(requestedArr);
        req.setRepeatDaily(false);

        // Force a conflicting association change on EVERY attempt
        AtomicInteger attemptCounter = new AtomicInteger(0);
        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            int count = attemptCounter.incrementAndGet();
            int alternatingBusId = (count % 2 == 1) ? fBusCId : fBusDId;
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                Schedule s = schedules.findById(fScheduleId).orElseThrow();
                Bus b = buses.findById(alternatingBusId).orElseThrow();
                s.setBus(b);
                schedules.save(s);
                return null;
            });
        });

        try {
            org.springframework.transaction.support.TransactionTemplate outerTx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);

            org.springframework.web.server.ResponseStatusException ex = assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> outerTx.execute(outerStatus ->
                            scheduleService.updateSchedule(fScheduleId, req)
                    )
            );

            assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
            assertEquals(ScheduleService.MAX_MATERIALIZE_RETRIES, attemptCounter.get(),
                    "Must exhaust exactly MAX_MATERIALIZE_RETRIES (" + ScheduleService.MAX_MATERIALIZE_RETRIES + ") attempts");

            // Verify no partial writes from the failed update request:
            // The requested departure/arrival times (10:00/13:00) must NEVER have been committed!
            Schedule reloaded = schedules.findById(fScheduleId).orElseThrow();
            assertEquals(initialDep, reloaded.getDepartureTime(), "Departure time must remain original; no partial write");
            assertEquals(initialArr, reloaded.getArrivalTime(), "Arrival time must remain original; no partial write");
            assertNotEquals(fBusBId, reloaded.getBus().getId(), "Requested bus B must not be committed on exhaustion");
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testOuterTransactionRetryExhaustionRollsBackCleanlyWithoutPartialWrites() {
        Route route = new Route();
        route.setOrigin("OuterExh-Org");
        route.setDestination("OuterExh-Dst");
        route.setDistanceKm(BigDecimal.valueOf(100.0));
        route.setBaseFare(BigDecimal.valueOf(700.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busA = new Bus();
        busA.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busA.setBusType("STANDARD");
        busA.setCapacity(40);
        busA.setStatus(Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);

        Bus busB = new Bus();
        busB.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busB.setBusType("LUXURY");
        busB.setCapacity(45);
        busB.setStatus(Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);

        Bus busC = new Bus();
        busC.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busC.setBusType("LUXURY");
        busC.setCapacity(45);
        busC.setStatus(Bus.BusStatus.ACTIVE);
        busC = buses.save(busC);
        eerService.syncSeats(busC);

        LocalDate targetDate = LocalDate.now().plusDays(3);
        Schedule template = new Schedule();
        template.setRoute(route);
        template.setBus(busA);
        template.setDepartureTime(LocalDate.now().atTime(7, 0));
        template.setArrivalTime(LocalDate.now().atTime(10, 0));
        template.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(LocalDate.now().plusDays(10));
        template = schedules.save(template);

        final int fTemplateId = template.getId();
        final int fBusBId = busB.getId();
        final int fBusCId = busC.getId();
        AtomicInteger hookCount = new AtomicInteger(0);

        scheduleService.setOnAfterCandidateDiscoveryHook(() -> {
            int count = hookCount.incrementAndGet();
            int nextBusId = (count % 2 == 1) ? fBusBId : fBusCId;
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                Schedule s = schedules.findById(fTemplateId).orElseThrow();
                Bus b = buses.findById(nextBusId).orElseThrow();
                s.setBus(b);
                schedules.save(s);
                return null;
            });
        });

        try {
            org.springframework.transaction.support.TransactionTemplate outerTx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);

            org.springframework.web.server.ResponseStatusException ex = assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> outerTx.execute(outerStatus ->
                            scheduleService.searchSchedules("OuterExh-Org", "OuterExh-Dst", targetDate)
                    )
            );
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());

            List<Schedule> occurrences = schedules.findByRecurrenceParentId(fTemplateId);
            assertTrue(occurrences.isEmpty(), "No partial database writes: occurrences must be empty upon retry exhaustion");
        } finally {
            scheduleService.setOnAfterCandidateDiscoveryHook(null);
        }
    }

    @Test
    void testCharterConfigurationStaleBusRaceScenario() throws Exception {
        // CONCRETE SCENARIO:
        // Group previous assigned bus is Bus A (capacity 20).
        // Target schedule initially uses Bus B (capacity 45).
        // Group passenger count is 30.
        // Discovery finds oldBusId = A, newBusId = B, preparing locks for {A, B}.
        // Another transaction changes target schedule to Bus A before locks are acquired.
        // If the code used stale newBusId (B), it would check capacity 45 for 30 passengers and PASS.
        // Because the code correctly validates the locked target bus (A, capacity 20),
        // it must REJECT with 400 Bad Request because Bus A cannot accommodate 30 passengers!

        User opUser = createUser("OPERATIONS_MANAGER", "OpPass123");
        StaffProfile opProfile = new StaffProfile();
        opProfile.setUser(opUser);
        opProfile.setEmployeeCode("OP-STALE-RACE");
        opProfile.setHireDate(LocalDate.now());
        opProfile.setStaffType("OPERATIONS_MANAGER");
        staff.save(opProfile);
        jakarta.servlet.http.Cookie opCookie = login(opUser, "OpPass123");

        User custUser = createUser("USER", "CustPass123");
        CustomerProfile custProfile = new CustomerProfile();
        custProfile.setUser(custUser);
        customers.save(custProfile);

        // Bus A: Capacity 20 (small bus)
        Bus busA = new Bus();
        busA.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busA.setBusType("MINI");
        busA.setCapacity(20);
        busA.setStatus(Bus.BusStatus.ACTIVE);
        busA = buses.save(busA);
        eerService.syncSeats(busA);

        // Bus B: Capacity 45 (large bus)
        Bus busB = new Bus();
        busB.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busB.setBusType("LUXURY");
        busB.setCapacity(45);
        busB.setStatus(Bus.BusStatus.ACTIVE);
        busB = buses.save(busB);
        eerService.syncSeats(busB);

        Route route = new Route();
        route.setOrigin("StaleBus-Org");
        route.setDestination("StaleBus-Dst");
        route.setDistanceKm(BigDecimal.valueOf(80.0));
        route.setBaseFare(BigDecimal.valueOf(500.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        // Old schedule assigned to Group with Bus A
        Schedule oldSchedule = new Schedule();
        oldSchedule.setRoute(route);
        oldSchedule.setBus(busA);
        oldSchedule.setDepartureTime(LocalDate.now().plusDays(4).atTime(8, 0));
        oldSchedule.setArrivalTime(LocalDate.now().plusDays(4).atTime(10, 0));
        oldSchedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        oldSchedule.setCharter(true);
        oldSchedule = schedules.save(oldSchedule);

        // Target new schedule initially using Bus B
        Schedule targetSchedule = new Schedule();
        targetSchedule.setRoute(route);
        targetSchedule.setBus(busB);
        targetSchedule.setDepartureTime(LocalDate.now().plusDays(4).atTime(14, 0));
        targetSchedule.setArrivalTime(LocalDate.now().plusDays(4).atTime(16, 0));
        targetSchedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        targetSchedule.setCharter(false);
        targetSchedule = schedules.save(targetSchedule);

        LocalDateTime depTarget = LocalDate.now().plusDays(4).atTime(14, 0);
        LocalDateTime arrTarget = LocalDate.now().plusDays(4).atTime(16, 0);

        GroupBooking group = new GroupBooking();
        group.setCustomerName("Stale Bus Test Group");
        group.setCustomerPhone("0771234567");
        group.setStartDate(depTarget);
        group.setEndDate(arrTarget);
        group.setPassengerCount(30); // 30 passengers! Fits on B (45), but EXCEEDS A (20)!
        group.setTotalCost(BigDecimal.valueOf(50000.0));
        group.setDepositAmount(BigDecimal.valueOf(15000.0));
        group.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        group.setAssignedBus(busA);
        group = groupBookings.save(group);
        eerService.syncGroup(group);

        group.getBooking().setSchedule(oldSchedule);
        group.getBooking().setCustomer(custProfile);
        bookings.save(group.getBooking());
        group = groupBookings.save(group);

        final int fGroupId = group.getId();
        final int fTargetScheduleId = targetSchedule.getId();
        final int fBusAId = busA.getId();
        final int fBusBId = busB.getId();
        final int fOldScheduleId = oldSchedule.getId();
        final int fCustId = custProfile.getId();

        // PART 1: Capacity failure scenario:
        // Hook changes targetSchedule from Bus B to Bus A between discovery and locking.
        eerController.setOnAfterGroupConfigDiscoveryHook(() -> {
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                Schedule s = schedules.findById(fTargetScheduleId).orElseThrow();
                Bus a = buses.findById(fBusAId).orElseThrow();
                s.setBus(a);
                schedules.save(s);
                return null;
            });
        });

        try {
            // Attempt configureGroup.
            // If stale bus B is validated: validation passes (30 <= 45).
            // When repaired: locked target bus A is validated: validation fails (30 > 20 capacity)!
            var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + fGroupId)
                    .cookie(opCookie)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of(
                            "customerId", fCustId,
                            "scheduleId", fTargetScheduleId,
                            "eventType", "Sports Trip"
                    ))))
                    .andReturn().getResponse();

            assertEquals(400, resp.getStatus(), "Must reject with 400 Bad Request due to insufficient capacity on actual locked bus");
            assertTrue(resp.getContentAsString().contains("exceeds bus capacity (20)"),
                    "Error message must specify insufficient capacity for the actual current bus: " + resp.getContentAsString());

            // Assert database consistency: no partial configuration changes
            GroupBooking committedGroup = groupBookings.findById(fGroupId).orElseThrow();
            assertEquals(GroupBooking.GroupBookingStatus.PENDING_REVIEW, committedGroup.getStatus());
            assertEquals(fBusAId, committedGroup.getAssignedBus().getId());

            Schedule committedTarget = schedules.findById(fTargetScheduleId).orElseThrow();
            assertFalse(committedTarget.isCharter(), "Target schedule must NOT be marked charter on failed configuration");

            Schedule committedOld = schedules.findById(fOldScheduleId).orElseThrow();
            assertTrue(committedOld.isCharter(), "Old schedule must remain charter when configuration failed");
        } finally {
            eerController.setOnAfterGroupConfigDiscoveryHook(null);
        }

        // PART 2: Valid capacity scenario:
        // Set passenger count to 15 (which comfortably fits Bus A's capacity of 20).
        group.setPassengerCount(15);
        group = groupBookings.save(group);

        // Reset targetSchedule initially back to Bus B
        Schedule resetTarget = schedules.findById(fTargetScheduleId).orElseThrow();
        resetTarget.setBus(busB);
        schedules.save(resetTarget);

        // Re-arm hook: switches targetSchedule from B to A between discovery and locking
        eerController.setOnAfterGroupConfigDiscoveryHook(() -> {
            org.springframework.transaction.support.TransactionTemplate tx =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                Schedule s = schedules.findById(fTargetScheduleId).orElseThrow();
                Bus a = buses.findById(fBusAId).orElseThrow();
                s.setBus(a);
                schedules.save(s);
                return null;
            });
        });

        try {
            var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/eer/groups/" + fGroupId)
                    .cookie(opCookie)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of(
                            "customerId", fCustId,
                            "scheduleId", fTargetScheduleId,
                            "eventType", "Valid Sports Trip"
                    ))))
                    .andReturn().getResponse();

            assertEquals(200, resp.getStatus(), "Must succeed with 200 OK when actual locked bus accommodates passenger count");

            // Committed invariants: Group assigned bus and linked schedule bus MUST agree!
            GroupBooking committedGroup = groupBookings.findById(fGroupId).orElseThrow();
            Schedule committedTarget = schedules.findById(fTargetScheduleId).orElseThrow();

            assertNotNull(committedGroup.getAssignedBus());
            assertNotNull(committedTarget.getBus());
            assertEquals(committedGroup.getAssignedBus().getId(), committedTarget.getBus().getId(),
                    "Group assigned bus and schedule bus MUST agree completely");
            assertEquals(fBusAId, committedGroup.getAssignedBus().getId(), "Must assign Bus A (the actual current locked bus)");
            assertTrue(committedTarget.isCharter(), "New schedule must be marked exclusive charter");

            // Verify parent booking schedule is updated and matches
            Booking committedBooking = bookings.findById(committedGroup.getBooking().getId()).orElseThrow();
            assertEquals(fTargetScheduleId, committedBooking.getSchedule().getId(), "Parent booking schedule must match new schedule");
            assertEquals(fBusAId, committedBooking.getSchedule().getBus().getId());
        } finally {
            eerController.setOnAfterGroupConfigDiscoveryHook(null);
        }
    }
}


