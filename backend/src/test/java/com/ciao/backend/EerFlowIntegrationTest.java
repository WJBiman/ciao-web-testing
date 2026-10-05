package com.ciao.backend;

import com.ciao.backend.controller.EerController;
import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.EerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EerFlowIntegrationTest {

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
    @Autowired private TicketRepository tickets;
    @Autowired private BranchRepository branches;
    @Autowired private ParcelRepository parcels;
    @Autowired private LostItemRepository lostItems;
    @Autowired private LostItemClaimRepository claims;
    @Autowired private GroupBookingRepository groupBookings;
    @Autowired private BookingRepository bookings;
    @Autowired private CustomerProfileRepository customers;
    @Autowired private StaffProfileRepository staff;
    @Autowired private ReservedSeatRepository allocations;
    @Autowired private EerService eerService;
    @Autowired private com.ciao.backend.service.BusService busService;
    @Autowired private com.ciao.backend.service.ReservationService reservationService;

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

    @Test
    void testDemoAccountsAreIdempotentAndNeverResetExistingPasswords(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var initializer = new com.ciao.backend.config.DemoAccountsInitializer();
        Map<String,Object> dependencies = Map.of("users", users, "roles", roles, "staff", staff,
                "branches", branches, "encoder", encoder, "eer", eerService, "json", json);
        dependencies.forEach((name, value) -> org.springframework.test.util.ReflectionTestUtils.setField(initializer, name, value));
        var accountList = com.ciao.backend.config.DemoAccountsInitializer.ACCOUNTS;
        Map<String,String> passwords = new java.util.HashMap<>();
        accountList.forEach(account -> passwords.put(account.email(), "LocalTestOnly-Password123"));
        var file = directory.resolve("credentials.json");
        json.writeValue(file.toFile(), passwords);
        org.springframework.test.util.ReflectionTestUtils.setField(initializer, "credentialsFile", file.toString());
        long before = users.count();
        initializer.run(null);
        assertEquals(before + 8, users.count());
        initializer.run(null);
        assertEquals(before + 8, users.count());
        User manager = users.findByEmailIgnoreCase("it25103647@ciao.test").orElseThrow();
        assertTrue(branches.findByManager_Id(staff.findByUserId(manager.getId()).orElseThrow().getId()).isPresent());
        User protectedUser = users.findByEmailIgnoreCase(accountList.get(0).email()).orElseThrow();
        String originalHash = protectedUser.getPasswordHash();
        passwords.put(accountList.get(0).email(), "AnotherTestPassword123");
        json.writeValue(file.toFile(), passwords);
        assertThrows(IllegalStateException.class, () -> initializer.run(null));
        assertEquals(originalHash, users.findById(protectedUser.getId()).orElseThrow().getPasswordHash());
    }

    @Test
    void testProposalStaffAccessAndFinanceGroupApproval() throws Exception {
        for (String type : List.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "FINANCE_MANAGER",
                "BRANCH_MANAGER", "E_TICKETING_COORDINATOR", "CUSTOMER_SERVICE_SUPERVISOR")) {
            User actor = createUser("STAFF", "RoleTestPass123");
            eerService.profile(actor);
            StaffProfile profile = staff.findByUserId(actor.getId()).orElseThrow();
            profile.setStaffType(type); staff.save(profile);
            Cookie cookie = login(actor, "RoleTestPass123");
            mvc.perform(get("/api/eer/access").cookie(cookie)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.staffType").value(type));
            if (!List.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER").contains(type)) {
                mvc.perform(post("/api/fleet/buses").cookie(cookie).contentType("application/json").content("{}"))
                        .andExpect(status().isForbidden());
            }
            if (type.equals("FINANCE_MANAGER")) {
                mvc.perform(get("/api/group-bookings").cookie(cookie)).andExpect(status().isOk());
                mvc.perform(get("/api/fleet/buses/active").cookie(cookie)).andExpect(status().isOk());
                Bus bus = buses.save(new Bus("FIN-TEST-01", 40, "AC", Bus.BusStatus.ACTIVE));
                GroupBooking group = new GroupBooking();
                group.setCustomerName("Finance Approval Test"); group.setCustomerPhone("0770044556");
                group.setStartDate(LocalDateTime.now().plusDays(60)); group.setEndDate(LocalDateTime.now().plusDays(61));
                group.setPassengerCount(20); group.setTotalCost(BigDecimal.valueOf(48000));
                group.setDepositAmount(BigDecimal.valueOf(14400)); groupBookings.save(group); eerService.syncGroup(group);
                mvc.perform(patch("/api/group-bookings/" + group.getId() + "/status").cookie(cookie)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("status", "APPROVED", "assignedBusId", bus.getId()))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
            }
            if (type.equals("BRANCH_MANAGER")) mvc.perform(get("/api/eer/access").cookie(cookie))
                    .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("checkin")));
            if (type.equals("CUSTOMER_SERVICE_SUPERVISOR")) mvc.perform(get("/api/group-bookings").cookie(cookie))
                    .andExpect(status().isForbidden());
        }
        User passenger = createUser("PASSENGER", "RoleTestPass123");
        mvc.perform(get("/api/eer/access").cookie(login(passenger, "RoleTestPass123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGroupRequestRetainsJourneyPreferencesAcrossStatusAndStaffLookup() throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("customerName", "Journey Test");
        payload.put("customerPhone", "0770000999");
        payload.put("startDate", LocalDateTime.now().plusDays(30).toString());
        payload.put("endDate", LocalDateTime.now().plusDays(31).toString());
        payload.put("passengerCount", 20);
        payload.put("totalCost", 1);
        payload.put("depositAmount", 0);
        payload.put("eventType", "SCHOOL");
        payload.put("preferredBusType", "MINI");
        payload.put("journeyDetails", "  Colombo to Kandy; pickup at school.  ");
        var created = mvc.perform(post("/api/group-bookings").contentType("application/json")
                .content(json.writeValueAsString(payload))).andExpect(status().isOk()).andReturn();
        int id = json.readTree(created.getResponse().getContentAsString()).get("id").asInt();
        groupBookings.flush();
        GroupBooking stored = groupBookings.findById(id).orElseThrow();
        assertEquals("SCHOOL", stored.getEventType());
        assertEquals("MINI", stored.getPreferredBusType());
        assertEquals("Colombo to Kandy; pickup at school.", stored.getJourneyDetails());
        mvc.perform(get("/api/group-bookings/status").param("reference", "GRP-" + id)
                .param("phone", "0770000999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventType").value("SCHOOL"))
                .andExpect(jsonPath("$.preferredBusType").value("MINI"))
                .andExpect(jsonPath("$.journeyDetails").value(stored.getJourneyDetails()))
                .andExpect(jsonPath("$.guestAccessToken").doesNotExist());
        User operator = createUser("STAFF", "SecretPass123");
        eerService.profile(operator);
        StaffProfile operatorProfile = staff.findByUserId(operator.getId()).orElseThrow();
        operatorProfile.setStaffType("OPERATIONS_MANAGER");
        staff.save(operatorProfile);
        mvc.perform(get("/api/group-bookings/" + id).cookie(login(operator, "SecretPass123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.journeyDetails").value(stored.getJourneyDetails()));
        long count = groupBookings.count();
        payload.put("journeyDetails", "x".repeat(2001));
        mvc.perform(post("/api/group-bookings").contentType("application/json")
                .content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        assertEquals(count, groupBookings.count(), "Oversized requests must not create a booking");
    }

    private Cookie login(User user, String rawPassword) throws Exception {
        var res = mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", user.getEmail(), "password", rawPassword))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = res.getResponse().getCookie("ciao_jwt");
        assertNotNull(cookie, "JWT cookie must be present");
        return cookie;
    }

    @Test
    void testFleetSeatConsistencyOnBusCreationAndCapacityChange() {
        Bus bus = new Bus();
        bus.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("LUXURY");
        bus.setCapacity(20);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        eerService.syncSeats(bus);

        List<BusSeat> seats = busSeats.findByBusId(bus.getId()).stream()
                .sorted(java.util.Comparator.comparingInt(s -> Integer.parseInt(s.getSeatNumber())))
                .toList();
        assertEquals(20, seats.size(), "Physical seats must match bus capacity 20");
        assertEquals("1", seats.get(0).getSeatNumber());
        assertEquals("20", seats.get(19).getSeatNumber());

        // Update capacity
        bus.setCapacity(25);
        bus = buses.save(bus);
        eerService.syncSeats(bus);

        List<BusSeat> updatedSeats = busSeats.findByBusId(bus.getId()).stream()
                .sorted(java.util.Comparator.comparingInt(s -> Integer.parseInt(s.getSeatNumber())))
                .toList();
        assertEquals(25, updatedSeats.size(), "Physical seats must sync up to 25 seats");
        assertEquals("25", updatedSeats.get(24).getSeatNumber());
    }

    @Test
    void testRealTicketCheckInAtomicAndRejections() throws Exception {
        User passenger = createUser("PASSENGER", "SecretPass123");
        User conductor = createUser("E_TICKETING_COORDINATOR", "SecretPass123");
        eerService.profile(conductor); // create staff profile
        Cookie conductorCookie = login(conductor, "SecretPass123");

        Bus bus = new Bus("NB-7788", 40, "AC", Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);
        Route route = routes.save(new Route("Colombo", "Kandy", BigDecimal.valueOf(1500.0), Route.RouteStatus.ACTIVE));

        Schedule schedule = new Schedule();
        schedule.setBus(bus);
        schedule.setRoute(route);
        schedule.setDepartureTime(LocalDateTime.now().plusHours(2));
        schedule.setArrivalTime(LocalDateTime.now().plusHours(5));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        Reservation reservation = new Reservation();
        reservation.setUser(passenger);
        reservation.setSchedule(schedule);
        reservation.setPassengerName("Nimal Perera");
        reservation.setPassengerPhone("0771234567");
        reservation.setTotalFare(BigDecimal.valueOf(1500.0));
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation = reservations.save(reservation);

        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setQrCode("QR-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setSeatRange("12");
        ticket = tickets.save(ticket);

        // 1. Successful check-in via QR
        mvc.perform(post("/api/eer/tickets/check-in")
                .cookie(conductorCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.CheckInInput(ticket.getQrCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passenger").value("Nimal Perera"))
                .andExpect(jsonPath("$.seats").value("12"));

        // 2. Reject already checked in ticket (atomic double-check)
        mvc.perform(post("/api/eer/tickets/check-in")
                .cookie(conductorCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.CheckInInput(ticket.getQrCode()))))
                .andExpect(status().isBadRequest());

        // 3. Reject check-in on cancelled trip
        Reservation reservation2 = new Reservation();
        reservation2.setUser(passenger);
        reservation2.setSchedule(schedule);
        reservation2.setPassengerName("Nimal Perera");
        reservation2.setPassengerPhone("0771234567");
        reservation2.setTotalFare(BigDecimal.valueOf(1500.0));
        reservation2.setStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation2 = reservations.save(reservation2);

        Ticket ticket2 = new Ticket();
        ticket2.setReservation(reservation2);
        ticket2.setQrCode("QR-" + UUID.randomUUID().toString().substring(0, 8));
        ticket2.setSeatRange("14");
        ticket2 = tickets.save(ticket2);

        schedule.setStatus(Schedule.ScheduleStatus.CANCELLED);
        schedules.save(schedule);

        mvc.perform(post("/api/eer/tickets/check-in")
                .cookie(conductorCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.CheckInInput(ticket2.getQrCode()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testStaffCanUpdateOwnNameAndUsernameWithoutCustomerProfile() throws Exception {
        User employee = createUser("STAFF", "StaffProfilePass123");
        eerService.profile(employee);
        Cookie employeeCookie = login(employee, "StaffProfilePass123");
        User passenger = createUser("PASSENGER", "PassengerProfilePass123");
        Cookie passengerCookie = login(passenger, "PassengerProfilePass123");

        mvc.perform(get("/api/eer/profile").cookie(employeeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.id").exists())
                .andExpect(jsonPath("$.customer.id").doesNotExist());

        String nextUsername = "staff_" + UUID.randomUUID().toString().substring(0, 8);
        EerController.StaffProfileInput input = new EerController.StaffProfileInput("Updated Staff Name", nextUsername);
        mvc.perform(put("/api/eer/profile/staff").cookie(employeeCookie)
                .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Staff Name"))
                .andExpect(jsonPath("$.username").value(nextUsername));
        mvc.perform(get("/api/auth/me").cookie(employeeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Staff Name"));

        mvc.perform(put("/api/eer/profile/staff").cookie(passengerCookie)
                .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/eer/profile/staff").cookie(employeeCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.StaffProfileInput("Duplicate Name", passenger.getUsername()))))
                .andExpect(status().isBadRequest());
        assertEquals("Updated Staff Name", users.findById(employee.getId()).orElseThrow().getFullName());
    }

    @Test
    void testCustomerProfilePhonesAndIsolation() throws Exception {
        User userA = createUser("PASSENGER", "PassA123");
        User userB = createUser("PASSENGER", "PassB123");

        Cookie cookieA = login(userA, "PassA123");
        Cookie cookieB = login(userB, "PassB123");

        // User A updates profile and multi-valued phones
        EerController.ProfileInput req = new EerController.ProfileInput(
                "Alice",
                "Perera",
                "45 Green Path, Colombo 03",
                "usr_alice_" + UUID.randomUUID().toString().substring(0, 6),
                List.of("0771234567", "0112345678")
        );

        mvc.perform(put("/api/eer/profile")
                .cookie(cookieA)
                .contentType("application/json")
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Perera"));

        // File a lost property item
        LostItem item = new LostItem();
        item.setItemDescription("Blue Leather Wallet");
        item.setReportedByName("Alice Perera");
        item.setReportedByPhone("0771234567");
        item.setStatus(LostItemStatus.FOUND);
        item = lostItems.save(item);

        // User A claims item
        EerController.ClaimInput claimReq = new EerController.ClaimInput(
                item.getId(),
                "National Identity Card matches name Alice Perera"
        );

        mvc.perform(post("/api/eer/claims")
                .cookie(cookieA)
                .contentType("application/json")
                .content(json.writeValueAsString(claimReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("PENDING"));

        // Verify User A sees claim
        mvc.perform(get("/api/eer/my-claims").cookie(cookieA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.itemDescription").value("Blue Leather Wallet"));

        // Verify User B does NOT see User A's claim (Data isolation)
        mvc.perform(get("/api/eer/my-claims").cookie(cookieB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testLostItemClaimLifecycleAndAtomicSupervisorDecision() throws Exception {
        User claimant = createUser("PASSENGER", "ClaimPass123");
        User supervisor = createUser("CUSTOMER_SERVICE_SUPERVISOR", "SuperPass123");
        eerService.profile(supervisor);
        Cookie claimantCookie = login(claimant, "ClaimPass123");
        Cookie supervisorCookie = login(supervisor, "SuperPass123");

        LostItem item = new LostItem();
        item.setItemDescription("Sony Headphones");
        item.setReportedByName("Baggage Handler");
        item.setReportedByPhone("0112233445");
        item.setStatus(LostItemStatus.FOUND);
        item = lostItems.save(item);

        // File claim
        mvc.perform(post("/api/eer/claims")
                .cookie(claimantCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.ClaimInput(item.getId(), "Serial number invoice"))))
                .andExpect(status().isOk());

        final Integer itemId = item.getId();
        LostItemClaim savedClaim = claims.findAll().stream()
                .filter(c -> c.getItem().getId().equals(itemId))
                .findFirst().orElseThrow();
        assertNotNull(savedClaim);

        // Supervisor approves claim atomically
        mvc.perform(put("/api/eer/claims/" + savedClaim.getId())
                .cookie(supervisorCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.ClaimDecision("APPROVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"));

        // Try deciding again on already resolved claim -> reject duplicate/conflict decision
        mvc.perform(put("/api/eer/claims/" + savedClaim.getId())
                .cookie(supervisorCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.ClaimDecision("APPROVED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGroupBookingDepositSimulatedPayment() throws Exception {
        User orgUser = createUser("PASSENGER", "GroupUser123");
        eerService.profile(orgUser);
        Cookie orgCookie = login(orgUser, "GroupUser123");

        Bus bus = new Bus("NB-9900", 30, "Luxury AC", Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        Route route = routes.save(new Route("Colombo", "Matara", BigDecimal.valueOf(2000.0), Route.RouteStatus.ACTIVE));
        Schedule schedule = new Schedule();
        schedule.setBus(bus);
        schedule.setRoute(route);
        schedule.setDepartureTime(LocalDateTime.now().plusDays(5));
        schedule.setArrivalTime(LocalDateTime.now().plusDays(5).plusHours(3));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        CustomerProfile customer = customers.findByUserId(orgUser.getId()).orElseThrow();

        Booking parentBooking = new Booking();
        parentBooking.setBookingType("GROUP");
        parentBooking.setBookingStatus("APPROVED");
        parentBooking.setTotalFare(BigDecimal.valueOf(50000.0));
        parentBooking.setCustomer(customer);
        parentBooking.setSchedule(schedule);
        parentBooking = bookings.save(parentBooking);

        GroupBooking gb = new GroupBooking();
        gb.setBooking(parentBooking);
        gb.setCustomerName("Engineering Batch Tour");
        gb.setCustomerPhone("0712345678");
        gb.setPassengerCount(25);
        gb.setStartDate(LocalDateTime.now().plusDays(5));
        gb.setEndDate(LocalDateTime.now().plusDays(7));
        gb.setTotalCost(BigDecimal.valueOf(50000.0));
        gb.setDepositAmount(BigDecimal.valueOf(15000.0));
        gb.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        gb.setAssignedBus(bus);
        gb = groupBookings.save(gb);

        // 1. Invalid card rejected
        EerController.GroupPaymentInput badPayment = new EerController.GroupPaymentInput(
                gb.getId(),
                null,
                "5555555555555555",
                "TEST",
                "12/30",
                "123"
        );

        mvc.perform(post("/api/eer/groups/pay-deposit")
                .cookie(orgCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(badPayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // 2. Unauthorized customer attempting to pay deposit for another customer's booking
        User hacker = createUser("CUSTOMER", "HackerPass123");
        eerService.profile(hacker);
        Cookie hackerCookie = login(hacker, "HackerPass123");

        EerController.GroupPaymentInput stealPayment = new EerController.GroupPaymentInput(
                gb.getId(),
                null,
                "4111111111111111",
                "CIAO TEST",
                "12/30",
                "123"
        );

        mvc.perform(post("/api/eer/groups/pay-deposit")
                .cookie(hackerCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(stealPayment)))
                .andExpect(status().isForbidden());

        // 3. Valid university test card accepted for actual owner
        EerController.GroupPaymentInput goodPayment = new EerController.GroupPaymentInput(
                gb.getId(),
                null,
                "4111111111111111",
                "CIAO TEST",
                "12/30",
                "123"
        );

        mvc.perform(post("/api/eer/groups/pay-deposit")
                .cookie(orgCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(goodPayment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        GroupBooking refreshedGb = groupBookings.findById(gb.getId()).orElseThrow();
        assertEquals(GroupBooking.GroupBookingStatus.DEPOSIT_PAID, refreshedGb.getStatus());

        // 4. Duplicate deposit payment attempt rejected
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .cookie(orgCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(goodPayment)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBranchManagerScopedPermissions() throws Exception {
        Branch branchA = new Branch();
        branchA.setLocation("Branch Colombo");
        branchA.setContactNumber("0112233445");
        branchA = branches.save(branchA);

        Branch branchB = new Branch();
        branchB.setLocation("Branch Kandy");
        branchB.setContactNumber("0812233445");
        branchB = branches.save(branchB);

        User managerA = createUser("STAFF", "MgrPass123");
        eerService.profile(managerA);
        StaffProfile spA = staff.findByUserId(managerA.getId()).orElseThrow();
        spA.setStaffType("BRANCH_MANAGER");
        staff.save(spA);
        branchA.setManager(spA);
        branches.save(branchA);

        Cookie mgrCookie = login(managerA, "MgrPass123");

        // Verify Branch Manager access to scoped management endpoint
        mvc.perform(get("/api/eer/management").cookie(mgrCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcels").isArray());

        Bus bus = buses.save(new Bus("ND-1122", 30, "Standard", Bus.BusStatus.ACTIVE));
        Route route = routes.save(new Route("Colombo", "Kandy", BigDecimal.valueOf(1000.0), Route.RouteStatus.ACTIVE));
        Schedule schedule = schedules.save(new Schedule(route, bus, null, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(3), Schedule.ScheduleStatus.SCHEDULED));

        User customerUser = createUser("PASSENGER", "CustPass999");
        eerService.profile(customerUser);
        CustomerProfile customer = customers.findByUserId(customerUser.getId()).orElseThrow();

        // Create parcel originating at Branch B
        Parcel parcelB = new Parcel();
        parcelB.setSenderName("External Sender");
        parcelB.setSenderPhone("0771122334");
        parcelB.setReceiverName("External Receiver");
        parcelB.setReceiverPhone("0772233445");
        parcelB.setWeight(BigDecimal.valueOf(2.5));
        parcelB.setTotalFee(BigDecimal.valueOf(500.0));
        parcelB.setStatus("RECEIVED");
        parcelB.setTrackingId("TRK-" + UUID.randomUUID().toString().substring(0, 8));
        parcelB.setBus(bus);
        parcelB.setOriginBranch(branchB);
        parcelB.setDestinationBranch(branchA);
        parcelB = parcels.save(parcelB);

        // Branch Manager A (managing Branch A) cannot reassign parcel originating at Branch B to an unauthorized branch
        EerController.ParcelInput assignInput = new EerController.ParcelInput(customer.getId(), branchB.getId(), branchB.getId(), schedule.getId());
        mvc.perform(put("/api/eer/parcels/" + parcelB.getId())
                .cookie(mgrCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(assignInput)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testItemLevelClaimConcurrencyProtection() throws Exception {
        User finder = createUser("CUSTOMER", "FindPass123");
        User claimant1 = createUser("CUSTOMER", "ClaimPass1");
        User claimant2 = createUser("CUSTOMER", "ClaimPass2");
        User supervisor = createUser("STAFF", "SuperPass1");
        eerService.profile(supervisor);
        StaffProfile sp = staff.findByUserId(supervisor.getId()).orElseThrow();
        sp.setStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        staff.save(sp);
        Cookie supCookie = login(supervisor, "SuperPass1");

        LostItem item = new LostItem();
        item.setItemDescription("Black leather wallet with ID card");
        item.setStatus(LostItemStatus.FOUND);
        item.setReportedByName("Finder Customer");
        item.setReportedByPhone("0771234567");
        item.setReportedBy(finder);
        item = lostItems.save(item);

        LostItemClaim claim1 = new LostItemClaim();
        claim1.setItem(item);
        claim1.setClaimant(claimant1);
        claim1.setProofOfOwnership("Drivers license inside");
        claim1.setClaimStatus("PENDING");
        claim1.setClaimDate(LocalDateTime.now());
        claim1 = claims.save(claim1);

        LostItemClaim claim2 = new LostItemClaim();
        claim2.setItem(item);
        claim2.setClaimant(claim2.getClaimant() != null ? claim2.getClaimant() : claimant2);
        claim2.setProofOfOwnership("Library card inside");
        claim2.setClaimStatus("PENDING");
        claim2.setClaimDate(LocalDateTime.now());
        claim2 = claims.save(claim2);

        // Approve claim 1
        mvc.perform(put("/api/eer/claims/" + claim1.getId())
                .cookie(supCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.ClaimDecision("APPROVED"))))
                .andExpect(status().isOk());

        LostItem updatedItem = lostItems.findById(item.getId()).orElseThrow();
        assertEquals(LostItemStatus.CLAIMED, updatedItem.getStatus());

        // Attempting to approve claim 2 for the same item must be rejected atomically
        mvc.perform(put("/api/eer/claims/" + claim2.getId())
                .cookie(supCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.ClaimDecision("APPROVED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCapacityReductionRejectionWhenActiveReservationsExist() {
        Bus bus = new Bus();
        bus.setPlateNumber("NB-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("STANDARD");
        bus.setCapacity(40);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);
        eerService.syncSeats(bus);

        Route route = new Route("Colombo", "Matara", BigDecimal.valueOf(1600.0), Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Schedule schedule = new Schedule(route, bus, null, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        User customer = createUser("CUSTOMER", "CustPass123");
        Reservation res = new Reservation(schedule, customer, "Test Passenger", "0771234567", BigDecimal.valueOf(1600.0), Reservation.ReservationStatus.CONFIRMED);
        res = reservations.save(res);

        ReservedSeat alloc = new ReservedSeat(res, "25", LocalDateTime.now().plusDays(2), ReservedSeat.SeatStatus.BOOKED);
        allocations.save(alloc);

        // Reducing bus capacity to 20 when seat 25 is actively reserved must be rejected
        boolean exceeds = eerService.hasActiveReservationsExceedingCapacity(bus.getId(), 20);
        assertTrue(exceeds, "Reducing capacity below active reserved seat number 25 must be flagged as conflict");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testTrulyConcurrentClaimApprovalsWithLatch() throws Exception {
        User finder = createUser("CUSTOMER", "FindPass1");
        User claimant1 = createUser("CUSTOMER", "ClaimantA");
        User claimant2 = createUser("CUSTOMER", "ClaimantB");
        User supervisor = createUser("STAFF", "SupervisorPass");
        eerService.profile(supervisor);
        StaffProfile sp = staff.findByUserId(supervisor.getId()).orElseThrow();
        sp.setStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        staff.save(sp);
        Cookie supCookie = login(supervisor, "SupervisorPass");

        LostItem item = new LostItem();
        item.setItemDescription("Silver luxury watch");
        item.setStatus(LostItemStatus.FOUND);
        item.setReportedByName("Finder");
        item.setReportedByPhone("0771122334");
        item.setReportedBy(finder);
        item = lostItems.save(item);

        LostItemClaim claim1 = new LostItemClaim();
        claim1.setItem(item);
        claim1.setClaimant(claimant1);
        claim1.setProofOfOwnership("Engraved back case");
        claim1.setClaimStatus("PENDING");
        claim1.setClaimDate(LocalDateTime.now());
        claim1 = claims.save(claim1);

        LostItemClaim claim2 = new LostItemClaim();
        claim2.setItem(item);
        claim2.setClaimant(claimant2);
        claim2.setProofOfOwnership("Original invoice from shop");
        claim2.setClaimStatus("PENDING");
        claim2.setClaimDate(LocalDateTime.now());
        claim2 = claims.save(claim2);

        final Integer c1Id = claim1.getId();
        final Integer c2Id = claim2.getId();

        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

        Runnable r1 = () -> {
            try {
                startLatch.await();
                var res = mvc.perform(put("/api/eer/claims/" + c1Id)
                        .cookie(supCookie)
                        .contentType("application/json")
                        .content("{\"status\":\"APPROVED\"}")).andReturn();
                if (res.getResponse().getStatus() == 200) successCount.incrementAndGet();
                else failCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable r2 = () -> {
            try {
                startLatch.await();
                var res = mvc.perform(put("/api/eer/claims/" + c2Id)
                        .cookie(supCookie)
                        .contentType("application/json")
                        .content("{\"status\":\"APPROVED\"}")).andReturn();
                if (res.getResponse().getStatus() == 200) successCount.incrementAndGet();
                else failCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(r1).start();
        new Thread(r2).start();
        startLatch.countDown();
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Exactly one claim approval must succeed");
        assertEquals(1, failCount.get(), "The competing claim approval must fail");
    }

    @Test
    void testGuestGroupPaymentAuthorizationAndInvalidTokenRejection() throws Exception {
        GroupBooking g = new GroupBooking();
        g.setCustomerName("Guest Traveler");
        g.setCustomerPhone("0779988776");
        g.setStartDate(LocalDateTime.now().plusDays(5));
        g.setEndDate(LocalDateTime.now().plusDays(6));
        g.setPassengerCount(25);
        g.setTotalCost(BigDecimal.valueOf(50000.0));
        g.setDepositAmount(BigDecimal.valueOf(15000.0));
        g.setStatus(GroupBooking.GroupBookingStatus.APPROVED);
        g.setGuestAccessToken(UUID.randomUUID().toString().replace("-", ""));
        g = groupBookings.save(g);
        eerService.syncGroup(g);

        // Missing or invalid guest token must be rejected with 400 Bad Request
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.GroupPaymentInput(
                        g.getId(), "WRONG_TOKEN", "4111111111111111", "CIAO TEST", "12/30", "123"))))
                .andExpect(status().isBadRequest());

        // Valid guest token must succeed
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.GroupPaymentInput(
                        g.getId(), g.getGuestAccessToken(), "4111111111111111", "CIAO TEST", "12/30", "123"))))
                .andExpect(status().isOk());

        // Re-attempting deposit payment must be rejected as already processed
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .contentType("application/json")
                .content(json.writeValueAsString(new EerController.GroupPaymentInput(
                        g.getId(), g.getGuestAccessToken(), "4111111111111111", "CIAO TEST", "12/30", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBranchManagerParcelDataIsolation() throws Exception {
        User mgrUser1 = createUser("STAFF", "BranchMgr1Pass");
        eerService.profile(mgrUser1);
        StaffProfile sp1 = staff.findByUserId(mgrUser1.getId()).orElseThrow();
        sp1.setStaffType("BRANCH_MANAGER");
        staff.save(sp1);
        Cookie mgr1Cookie = login(mgrUser1, "BranchMgr1Pass");

        User mgrUser2 = createUser("STAFF", "BranchMgr2Pass");
        eerService.profile(mgrUser2);
        StaffProfile sp2 = staff.findByUserId(mgrUser2.getId()).orElseThrow();
        sp2.setStaffType("BRANCH_MANAGER");
        staff.save(sp2);
        Cookie mgr2Cookie = login(mgrUser2, "BranchMgr2Pass");

        Branch branch1 = new Branch();
        branch1.setLocation("Colombo Fort");
        branch1.setContactNumber("0112334455");
        branch1.setManager(sp1);
        branch1 = branches.save(branch1);

        Branch branch2 = new Branch();
        branch2.setLocation("Kandy City");
        branch2.setContactNumber("0812334455");
        branch2.setManager(sp2);
        branch2 = branches.save(branch2);

        Branch branch3 = new Branch();
        branch3.setLocation("Galle Central");
        branch3.setContactNumber("0912334455");
        branch3 = branches.save(branch3);

        Bus bus = new Bus();
        bus.setPlateNumber("NA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("STANDARD");
        bus.setCapacity(30);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        // Parcel for Branch 2 <-> Branch 3
        Parcel p23 = new Parcel();
        p23.setTrackingId("CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p23.setBus(bus);
        p23.setSenderName("Sender 2");
        p23.setSenderPhone("0772222222");
        p23.setReceiverName("Receiver 3");
        p23.setReceiverPhone("0773333333");
        p23.setWeight(BigDecimal.valueOf(5.0));
        p23.setTotalFee(BigDecimal.valueOf(1000.0));
        p23.setStatus("PENDING");
        p23.setOriginBranch(branch2);
        p23.setDestinationBranch(branch3);
        p23 = parcels.save(p23);

        // Manager 1 (Branch 1) must be forbidden from accessing Parcel p23
        mvc.perform(get("/api/parcels/" + p23.getId())
                .cookie(mgr1Cookie))
                .andExpect(status().isForbidden());

        // Manager 2 (Branch 2) must be allowed to access Parcel p23
        mvc.perform(get("/api/parcels/" + p23.getId())
                .cookie(mgr2Cookie))
                .andExpect(status().isOk());
    }

    @Test
    void testRecurringScheduleBusBackedSearchAndSelfConflict() throws Exception {
        Route route = new Route();
        route.setOrigin("Matara");
        route.setDestination("Colombo");
        route.setDistanceKm(BigDecimal.valueOf(160.0));
        route.setBaseFare(BigDecimal.valueOf(1200.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = new Bus();
        bus.setPlateNumber("NC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("LUXURY");
        bus.setCapacity(45);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);
        eerService.syncSeats(bus);

        // Create recurring daily template starting today at 08:00 AM
        LocalDateTime today0800 = java.time.LocalDate.now().atTime(8, 0);
        Schedule template = new Schedule();
        template.setRoute(route);
        template.setBus(bus);
        template.setDepartureTime(today0800);
        template.setArrivalTime(today0800.plusHours(3));
        template.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(java.time.LocalDate.now().plusDays(10));
        template = schedules.save(template);
        if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            org.springframework.test.context.transaction.TestTransaction.start();
        }

        // Search on tomorrow (day + 1): Occurrence must be materialized successfully with assigned bus!
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);
        List<Schedule> foundTomorrow = eerService != null ?
                schedules.searchSchedules("Matara", "Colombo", java.time.LocalDateTime.now(), tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay()) :
                List.of();

        // Trigger ScheduleService search via MockMvc
        User passenger = createUser("USER", "PassPass123");
        Cookie passCookie = login(passenger, "PassPass123");

        mvc.perform(get("/api/schedules/search")
                .param("origin", "Matara")
                .param("destination", "Colombo")
                .param("date", tomorrow.toString())
                .cookie(passCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].bus.id").value(bus.getId()));

        // Also search on day + 3: Occurrence must also be materialized without self-conflict!
        java.time.LocalDate day3 = java.time.LocalDate.now().plusDays(3);
        mvc.perform(get("/api/schedules/search")
                .param("origin", "Matara")
                .param("destination", "Colombo")
                .param("date", day3.toString())
                .cookie(passCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].bus.id").value(bus.getId()));
    }

    @Test
    void testOvernightAndIndefiniteRecurrenceConflict() {
        Route route = new Route();
        route.setOrigin("Kandy");
        route.setDestination("Jaffna");
        route.setDistanceKm(BigDecimal.valueOf(320.0));
        route.setBaseFare(BigDecimal.valueOf(2500.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus busInit = new Bus();
        busInit.setPlateNumber("ND-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        busInit.setBusType("SUPER_LUXURY");
        busInit.setCapacity(40);
        busInit.setStatus(Bus.BusStatus.ACTIVE);
        final Bus bus = buses.save(busInit);

        // 1. Create overnight recurring daily service (starts 22:00, arrives next day 04:00)
        LocalDateTime startNight = java.time.LocalDate.now().atTime(22, 0);
        Schedule overnightTmpl = new Schedule();
        overnightTmpl.setRoute(route);
        overnightTmpl.setBus(bus);
        overnightTmpl.setDepartureTime(startNight);
        overnightTmpl.setArrivalTime(startNight.plusHours(6)); // Arrives next day 04:00
        overnightTmpl.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        overnightTmpl.setRepeatDaily(true);
        overnightTmpl.setRepeatUntil(null); // Indefinite
        schedules.save(overnightTmpl);

        // A charter requesting bus on next day between 02:00 and 06:00 MUST conflict with overnight trip!
        LocalDateTime charterStart = java.time.LocalDate.now().plusDays(1).atTime(2, 0);
        LocalDateTime charterEnd = java.time.LocalDate.now().plusDays(1).atTime(6, 0);

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            eerService.validateBusAssignmentForCharter(bus.getId(), null, charterStart, charterEnd, 20);
        });

        // A trip requesting bus 90 days in the future between 23:00 and 02:00 MUST also conflict (no 60-day limit!)
        LocalDateTime future90Dep = java.time.LocalDate.now().plusDays(90).atTime(23, 0);
        LocalDateTime future90Arr = future90Dep.plusHours(3);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            eerService.validateBusAvailableForPublicTrip(bus.getId(), null, future90Dep, future90Arr, false, null);
        });
    }

    @Test
    void testGuestTokenIsolationAndRecovery() throws Exception {
        // Create guest group booking
        GroupBooking g1 = new GroupBooking();
        g1.setCustomerName("Guest Alpha");
        g1.setCustomerPhone("0771112222");
        g1.setStartDate(LocalDateTime.now().plusDays(5));
        g1.setEndDate(LocalDateTime.now().plusDays(6));
        g1.setPassengerCount(25);
        g1.setTotalCost(BigDecimal.valueOf(50000.0));
        g1.setDepositAmount(BigDecimal.valueOf(15000.0));
        g1.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        String token1 = UUID.randomUUID().toString();
        g1.setGuestAccessToken(token1);
        g1 = groupBookings.save(g1);
        eerService.syncGroup(g1);

        GroupBooking g2 = new GroupBooking();
        g2.setCustomerName("Guest Beta");
        g2.setCustomerPhone("0773334444");
        g2.setStartDate(LocalDateTime.now().plusDays(7));
        g2.setEndDate(LocalDateTime.now().plusDays(8));
        g2.setPassengerCount(30);
        g2.setTotalCost(BigDecimal.valueOf(60000.0));
        g2.setDepositAmount(BigDecimal.valueOf(18000.0));
        g2.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        String token2 = UUID.randomUUID().toString();
        g2.setGuestAccessToken(token2);
        g2 = groupBookings.save(g2);
        eerService.syncGroup(g2);

        // 1. Status response MUST NOT disclose guestAccessToken
        mvc.perform(get("/api/group-bookings/status")
                .param("reference", "GRP-" + g1.getId())
                .param("phone", "0771112222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestAccessToken").doesNotExist());

        // 2. Token from booking 1 CANNOT pay or authorize booking 2
        Map<String, Object> payPayload = Map.of(
                "groupId", g2.getId(),
                "guestToken", token1, // Wrong token!
                "cardNumber", "4111111111111111",
                "cardholderName", "CIAO TEST",
                "expiry", "12/30",
                "cvv", "123"
        );
        mvc.perform(post("/api/eer/groups/pay-deposit")
                .contentType("application/json")
                .content(json.writeValueAsString(payPayload)))
                .andExpect(status().isBadRequest());

        // 3. Unauthenticated/public customer knowledge-based recovery attempt MUST be rejected!
        Map<String, String> recoveryPayload = Map.of(
                "reference", "GRP-" + g1.getId(),
                "phone", "0771112222",
                "customerName", "Guest Alpha"
        );
        mvc.perform(post("/api/group-bookings/recover-token")
                .contentType("application/json")
                .content(json.writeValueAsString(recoveryPayload)))
                .andExpect(status().isUnauthorized());

        // 4. Authorized Staff-assisted recovery workflow succeeds
        User coordinatorUser = createUser("STAFF", "CoordPass123");
        eerService.profile(coordinatorUser);
        StaffProfile spCoord = staff.findByUserId(coordinatorUser.getId()).orElseThrow();
        spCoord.setStaffType("E_TICKETING_COORDINATOR");
        staff.save(spCoord);
        Cookie coordCookie = login(coordinatorUser, "CoordPass123");

        mvc.perform(post("/api/group-bookings/recover-token")
                .cookie(coordCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(recoveryPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestAccessToken").value(token1));
    }

    @Test
    void testCharterAssignmentRejectsPublicScheduleWithActiveReservations() throws Exception {
        User opUser = createUser("STAFF", "OpPass123");
        eerService.profile(opUser);
        StaffProfile sp = staff.findByUserId(opUser.getId()).orElseThrow();
        sp.setStaffType("OPERATIONS_MANAGER");
        staff.save(sp);
        Cookie opCookie = login(opUser, "OpPass123");

        Route route = new Route();
        route.setOrigin("Colombo");
        route.setDestination("Kandy");
        route.setDistanceKm(BigDecimal.valueOf(115.0));
        route.setBaseFare(BigDecimal.valueOf(1000.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = new Bus();
        bus.setPlateNumber("NF-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("SUPER_LUXURY");
        bus.setCapacity(40);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setBus(bus);
        schedule.setDepartureTime(LocalDateTime.now().plusDays(2));
        schedule.setArrivalTime(LocalDateTime.now().plusDays(2).plusHours(3));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        // Add confirmed passenger reservation on seat 5
        User passenger = createUser("PASSENGER", "PassCharterTest");
        CustomerProfile customerProfile = eerService.customer(passenger);

        Reservation res = new Reservation(schedule, passenger, "Charter Passenger", "0779998877", BigDecimal.valueOf(1000.0), Reservation.ReservationStatus.CONFIRMED);
        res = reservations.save(res);

        ReservedSeat rs = new ReservedSeat();
        rs.setReservation(res);
        rs.setSeatNumber("5");
        rs.setStatus(ReservedSeat.SeatStatus.BOOKED);
        allocations.save(rs);

        // Group booking attempting to configure this schedule as private charter must be rejected!
        GroupBooking charter = new GroupBooking();
        charter.setCustomerName("Corporate Alpha");
        charter.setCustomerPhone("0778881122");
        charter.setStartDate(schedule.getDepartureTime());
        charter.setEndDate(schedule.getArrivalTime());
        charter.setPassengerCount(30);
        charter.setTotalCost(BigDecimal.valueOf(80000.0));
        charter.setDepositAmount(BigDecimal.valueOf(24000.0));
        charter.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);
        charter = groupBookings.save(charter);
        eerService.syncGroup(charter);

        Map<String, Object> configurePayload = Map.of(
                "customerId", customerProfile.getId(),
                "scheduleId", schedule.getId(),
                "eventType", "Corporate Trip"
        );

        mvc.perform(put("/api/eer/groups/" + charter.getId())
                .cookie(opCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(configurePayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCheckInWithFlexibleFormats() throws Exception {
        User staffUser = createUser("STAFF", "CheckInStaffPass");
        eerService.profile(staffUser);
        StaffProfile sp = staff.findByUserId(staffUser.getId()).orElseThrow();
        sp.setStaffType("E_TICKETING_COORDINATOR");
        staff.save(sp);
        Cookie staffCookie = login(staffUser, "CheckInStaffPass");

        Route route = new Route();
        route.setOrigin("Galle");
        route.setDestination("Colombo");
        route.setDistanceKm(BigDecimal.valueOf(120.0));
        route.setBaseFare(BigDecimal.valueOf(800.0));
        route.setStatus(Route.RouteStatus.ACTIVE);
        route = routes.save(route);

        Bus bus = new Bus();
        bus.setPlateNumber("NE-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setBusType("EXPRESS");
        bus.setCapacity(30);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        Schedule schedule = new Schedule();
        schedule.setRoute(route);
        schedule.setBus(bus);
        schedule.setDepartureTime(LocalDateTime.now().plusHours(1)); // Inside 4-hour window
        schedule.setArrivalTime(LocalDateTime.now().plusHours(3));
        schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        schedule = schedules.save(schedule);

        User passenger = createUser("USER", "Pass123");
        Reservation res = new Reservation(schedule, passenger, "Test Pass", "0770001111", BigDecimal.valueOf(800.0), Reservation.ReservationStatus.CONFIRMED);
        res = reservations.save(res);

        Ticket ticket = new Ticket();
        ticket.setReservation(res);
        ticket.setIssueDate(LocalDateTime.now());
        ticket.setQrCode("CIAO-TICKET:" + UUID.randomUUID());
        ticket.setSeatRange("12");
        ticket = tickets.save(ticket);

        // Check in using QR code directly
        mvc.perform(post("/api/eer/tickets/check-in")
                .cookie(staffCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("qrCode", ticket.getQrCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passenger").value("Test Pass"))
                .andExpect(jsonPath("$.seats").value("12"));

        // Duplicate check-in must be rejected
        mvc.perform(post("/api/eer/tickets/check-in")
                .cookie(staffCookie)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("qrCode", ticket.getQrCode()))))
                .andExpect(status().isBadRequest());
    }
}
