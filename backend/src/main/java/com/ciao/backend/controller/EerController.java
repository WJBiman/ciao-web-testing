package com.ciao.backend.controller;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.EerService;
import com.ciao.backend.service.HardDeleteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/eer")
@Transactional
public class EerController {
    @Autowired EerService eer;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CustomerProfileRepository customers;
    @Autowired StaffProfileRepository staff;
    @Autowired BranchRepository branches;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository stops;
    @Autowired BusRepository buses;
    @Autowired DriverRepository drivers;
    @Autowired ScheduleRepository schedules;
    @Autowired ParcelRepository parcels;
    @Autowired GroupBookingRepository groups;
    @Autowired PaymentRepository payments;
    @Autowired LostItemRepository lostItems;
    @Autowired LostItemClaimRepository claims;
    @Autowired NotificationRepository notifications;
    @Autowired UserPhoneRepository phones;
    @Autowired TicketRepository tickets;
    @Autowired BookingRepository bookings;
    @Autowired com.ciao.backend.repository.ReservationRepository reservations;
    @Autowired ReservedSeatRepository reservedSeats;
    @Autowired CancellationRequestRepository cancellationRequests;
    @Autowired HardDeleteService hardDelete;
    @Autowired PasswordEncoder encoder;

    // Test hook for deterministic interleaving in configureGroup
    private volatile Runnable onAfterGroupConfigDiscoveryHook = null;

    public void setOnAfterGroupConfigDiscoveryHook(Runnable hook) {
        this.onAfterGroupConfigDiscoveryHook = hook;
    }

    private static final Set<String> TYPES = Set.of("SYSTEM_ADMINISTRATOR","BRANCH_MANAGER","OPERATIONS_MANAGER","FINANCE_MANAGER","E_TICKETING_COORDINATOR","CUSTOMER_SERVICE_SUPERVISOR");
    private User user() {
        User u = eer.currentUser();
        if (u == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String name = auth.getName();
                if (name != null && !name.isBlank()) {
                    u = users.findByEmailIgnoreCase(name)
                            .or(() -> users.findByUsernameIgnoreCase(name))
                            .or(() -> users.findByPhone(name))
                            .orElse(null);
                }
            }
        }
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return u;
    }
    private StaffProfile permit(String... types) {
        User u=user();
        Optional<StaffProfile> maybeProfile = staff.findByUserId(u.getId());
        if (maybeProfile.isEmpty()) {
            if (u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleName().replace("ROLE_", "").trim())) {
                // Synthesize or find admin profile
                return staff.findAll().stream()
                        .filter(sp -> "SYSTEM_ADMINISTRATOR".equals(sp.getStaffType()))
                        .findFirst()
                        .orElse(null);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff account required.");
        }
        StaffProfile p = maybeProfile.get();
        if (!"SYSTEM_ADMINISTRATOR".equals(p.getStaffType()) && !Arrays.asList(types).contains(p.getStaffType())) {
            if (u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleName().replace("ROLE_", "").trim())) {
                return p;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This action requires the appropriate staff role.");
        }
        return p;
    }
    private <T> T require(Optional<T> value) { return value.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found")); }
    private void check(boolean ok, String message) { if(!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private StaffProfile typed(Integer id,String type) {
        if(id==null)return null;
        StaffProfile p=require(staff.findById(id)); check(type.equals(p.getStaffType()),"Selected employee must be "+type); return p;
    }

    @DeleteMapping("/{module}/{id}")
    public Map<String, Object> deleteManagedRecord(@PathVariable String module, @PathVariable Integer id) {
        switch (module) {
            case "staff", "branches" -> permit();
            case "routes", "buses", "schedules", "drivers" -> permit("OPERATIONS_MANAGER");
            case "groups" -> permit("OPERATIONS_MANAGER", "FINANCE_MANAGER");
            case "parcels" -> permit("BRANCH_MANAGER");
            case "payments" -> permit("FINANCE_MANAGER");
            case "claims", "cancellations", "lost-items" -> permit("CUSTOMER_SERVICE_SUPERVISOR");
            case "reservations" -> permit("E_TICKETING_COORDINATOR");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delete is unavailable for this module.");
        }
        hardDelete.delete(module, id, user().getId());
        return Map.of("deleted", true, "module", module, "id", id);
    }

    private List<String> permissions(String type) {
        return switch(type) {
            case "SYSTEM_ADMINISTRATOR" -> List.of("staff","branches","routes","buses","parcels","groups","payments","claims","cancellations","reservations","checkin");
            case "OPERATIONS_MANAGER" -> List.of("routes","buses","groups");
            case "BRANCH_MANAGER" -> List.of("parcels","checkin");
            case "FINANCE_MANAGER" -> List.of("payments","groups");
            case "E_TICKETING_COORDINATOR" -> List.of("groups","reservations","checkin");
            case "CUSTOMER_SERVICE_SUPERVISOR" -> List.of("claims","cancellations");
            default -> List.of();
        };
    }

    @GetMapping("/access") public Map<String,Object> access() {
        StaffProfile actor=permit("BRANCH_MANAGER","OPERATIONS_MANAGER","FINANCE_MANAGER","E_TICKETING_COORDINATOR","CUSTOMER_SERVICE_SUPERVISOR");
        return Map.of("staffType", actor.getStaffType(), "permissions", permissions(actor.getStaffType()));
    }

    @GetMapping("/management") public Map<String,Object> management() {
        StaffProfile actor=permit("BRANCH_MANAGER","OPERATIONS_MANAGER","FINANCE_MANAGER","E_TICKETING_COORDINATOR","CUSTOMER_SERVICE_SUPERVISOR");
        Map<String,Object> result = new HashMap<>(Map.ofEntries(Map.entry("staff",staff.findAll()),Map.entry("branches",branches.findAll()),Map.entry("customers",customers.findAll()),
            Map.entry("routes",routes.findAll()),Map.entry("stops",stops.findAll()),Map.entry("buses",buses.findAll()),Map.entry("drivers",drivers.findAll()),
            Map.entry("schedules",schedules.findAll()),Map.entry("parcels",parcels.findAll()),Map.entry("groups",groups.findAll()),
            Map.entry("payments",payments.findAll()),Map.entry("claims",claims.findWithDetailsAll()),Map.entry("lostItems",lostItems.findAll()), Map.entry("reservations",reservations.findAll()),
            Map.entry("cancellationRequests",cancellationRequests.findAll())));
        String type=actor.getStaffType();
        List<String> allowed=permissions(type);
        if(!allowed.contains("payments"))result.put("payments",List.of());
        if(!allowed.contains("claims")){result.put("claims",List.of());result.put("lostItems",List.of());}
        if(!allowed.contains("cancellations"))result.put("cancellationRequests",List.of());
        if(!allowed.contains("groups"))result.put("groups",List.of());
        if(!allowed.contains("reservations"))result.put("reservations",List.of());
        if(!allowed.contains("parcels")) {
            result.put("parcels",List.of());
        } else if ("BRANCH_MANAGER".equals(type)) {
            var managedBranch = branches.findByManager_Id(actor.getId());
            if (managedBranch.isPresent()) {
                Integer mgrBranchId = managedBranch.get().getId();
                List<Parcel> branchParcels = parcels.findAll().stream()
                        .filter(p -> (p.getOriginBranch() != null && p.getOriginBranch().getId().equals(mgrBranchId))
                                || (p.getDestinationBranch() != null && p.getDestinationBranch().getId().equals(mgrBranchId)))
                        .toList();
                result.put("parcels", branchParcels.isEmpty() ? parcels.findAll() : branchParcels);
            } else {
                result.put("parcels", parcels.findAll());
            }
        }
        result.put("staffType",type);
        result.put("permissions",allowed.stream().map(key->Map.of("id",key)).toList());
        return result;
    }
    public record StaffInput(Integer id,@NotBlank String name,@Email @NotBlank String email,@NotBlank String phone,String password,
        @NotBlank String employeeCode,@NotNull LocalDate hireDate,@NotBlank String staffType) {}
    @PostMapping("/staff") public StaffProfile saveStaff(@Valid @RequestBody StaffInput input) {
        permit(); check(TYPES.contains(input.staffType()),"Choose a supported staff role.");
        StaffProfile p=input.id()==null?new StaffProfile():require(staff.findById(input.id()));
        User u=p.getUser();
        String normalizedPhone=com.ciao.backend.security.AccountIdentifiers.normalize(input.phone());
        if(u==null) {
            check(input.password()!=null && input.password().length()>=8 && input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"Use a password of at least 8 characters and at most 72 bytes.");
            check(!users.existsByEmailIgnoreCase(input.email()),"Email already in use.");
            check(!users.existsByPhoneIn(com.ciao.backend.security.AccountIdentifiers.phoneForms(normalizedPhone)),"Phone already in use.");
            u=new User(); u.setPasswordHash(encoder.encode(input.password())); u.setPhone(normalizedPhone);
            u.setRole(require(roles.findByRoleName("STAFF")));
        } else {
            // Editing existing staff
            var existingByEmail = users.findByEmailIgnoreCase(input.email().trim());
            check(existingByEmail.isEmpty() || existingByEmail.get().getId().equals(u.getId()), "Email already in use by another account.");
            var existingByPhone = users.findByPhone(normalizedPhone);
            check(existingByPhone.isEmpty() || existingByPhone.get().getId().equals(u.getId()), "Phone number already in use by another account.");
            u.setPhone(normalizedPhone);
        }
        if (p.getId() != null && !p.getStaffType().equals(input.staffType())) {
            // Check if existing BRANCH_MANAGER manages an active branch
            if ("BRANCH_MANAGER".equals(p.getStaffType())) {
                boolean managesBranch = branches.findAll().stream().anyMatch(b -> b.getManager() != null && b.getManager().getId().equals(p.getId()));
                check(!managesBranch, "Cannot change role of Branch Manager while assigned to manage a branch. Reassign the branch first.");
            }
            // Check if existing OPERATIONS_MANAGER oversees routes or registered buses
            if ("OPERATIONS_MANAGER".equals(p.getStaffType())) {
                boolean overseesRoute = routes.findAll().stream().anyMatch(r -> r.getOverseenBy() != null && r.getOverseenBy().getId().equals(p.getId()));
                check(!overseesRoute, "Cannot change role of Operations Manager while overseeing active routes. Reassign the route first.");
                boolean registeredBus = buses.findAll().stream().anyMatch(b -> b.getRegisteredBy() != null && b.getRegisteredBy().getId().equals(p.getId()));
                check(!registeredBus, "Cannot change role of Operations Manager who registered buses. Reassign bus registration first.");
            }
        }
        if(p.getId()!=null && p.getStaffType().equals("SYSTEM_ADMINISTRATOR")) check(input.staffType().equals("SYSTEM_ADMINISTRATOR"),"System administrator role cannot be removed here.");
        u.setFullName(input.name().trim());u.setEmail(input.email().trim().toLowerCase());
        users.save(u);p.setUser(u);p.setEmployeeCode(input.employeeCode());p.setHireDate(input.hireDate());p.setStaffType(input.staffType());
        return staff.save(p);
    }
    public record BranchInput(Integer id,@NotBlank String location,@NotBlank String contactNumber,@NotNull Integer managerId) {}
    @PostMapping("/branches") public Branch branch(@Valid @RequestBody BranchInput input) {
        permit(); Branch b=input.id()==null?new Branch():require(branches.findById(input.id()));
        b.setLocation(input.location());b.setContactNumber(input.contactNumber());b.setManager(typed(input.managerId(),"BRANCH_MANAGER"));return branches.save(b);
    }
    public record RouteInput(@NotNull @DecimalMin("0.01") BigDecimal distanceKm,@NotNull Integer managerId,@NotEmpty List<@NotBlank String> stops) {}
    @PutMapping("/routes/{id}") public Route configureRoute(@PathVariable Integer id,@Valid @RequestBody RouteInput input) {
        permit("OPERATIONS_MANAGER"); Route r=require(routes.findById(id));r.setDistanceKm(input.distanceKm());r.setOverseenBy(typed(input.managerId(),"OPERATIONS_MANAGER"));
        stops.deleteAll(stops.findByRouteIdOrderBySequenceNumber(id));stops.flush();int sequence=1;
        for(String name:input.stops()){RouteStop s=new RouteStop();s.setRoute(r);s.setLocationName(name.trim());s.setSequenceNumber(sequence++);stops.save(s);}
        return routes.save(r);
    }
    public record BusInput(@NotBlank String busType,@NotNull Integer managerId,Integer driverId) {}
    @PutMapping("/buses/{id}") public Bus configureBus(@PathVariable Integer id,@Valid @RequestBody BusInput input) {
        permit("OPERATIONS_MANAGER"); Bus b=require(buses.findById(id)); b.setBusType(input.busType());b.setRegisteredBy(typed(input.managerId(),"OPERATIONS_MANAGER"));
        if(input.driverId()!=null){Driver d=require(drivers.findById(input.driverId()));d.setAssignedBus(b);drivers.save(d);}eer.syncSeats(b);return buses.save(b);
    }
    public record ParcelInput(@NotNull Integer customerId,@NotNull Integer originBranchId,@NotNull Integer destinationBranchId,@NotNull Integer scheduleId) {}
    @PutMapping("/parcels/{id}") public Parcel assignParcel(@PathVariable Integer id,@Valid @RequestBody ParcelInput input) {
        StaffProfile actor = permit("BRANCH_MANAGER");
        check(!input.originBranchId().equals(input.destinationBranchId()),"Branches must differ.");
        Parcel p = require(parcels.findById(id));
        if ("BRANCH_MANAGER".equals(actor.getStaffType())) {
            var managedBranch = branches.findAll().stream().filter(b -> b.getManager() != null && b.getManager().getId().equals(actor.getId())).findFirst();
            if (managedBranch.isPresent()) {
                Integer mgrBranchId = managedBranch.get().getId();
                
                // Check existing parcel scope: Cannot modify a parcel that does not belong to the manager's branch
                if (p.getOriginBranch() != null && p.getDestinationBranch() != null) {
                    boolean existingBelongs = p.getOriginBranch().getId().equals(mgrBranchId) || p.getDestinationBranch().getId().equals(mgrBranchId);
                    check(existingBelongs, "Unauthorized: Cannot reassign a parcel that belongs to another branch.");
                }
                // Check target parcel scope
                boolean targetBelongs = input.originBranchId().equals(mgrBranchId) || input.destinationBranchId().equals(mgrBranchId);
                check(targetBelongs, "Branch Managers can only dispatch or receive parcels originating from or destined for their own managed branch.");
            }
        }
        Schedule s = require(schedules.findById(input.scheduleId()));
        check(s.getBus()!=null && s.getStatus()==Schedule.ScheduleStatus.SCHEDULED && s.getDepartureTime().isAfter(LocalDateTime.now()),"Select a future scheduled trip with a bus.");
        p.setCustomer(require(customers.findById(input.customerId())));p.setOriginBranch(require(branches.findById(input.originBranchId())));p.setDestinationBranch(require(branches.findById(input.destinationBranchId())));
        p.setSchedule(s);p.setBus(s.getBus());return parcels.save(p);
    }
    public record GroupInput(@NotNull Integer customerId,@NotNull Integer scheduleId,@NotBlank String eventType) {}
    @PutMapping("/groups/{id}")
    @org.springframework.transaction.annotation.Transactional
    public GroupBooking configureGroup(@PathVariable Integer id,@Valid @RequestBody GroupInput input) {
        permit("OPERATIONS_MANAGER","E_TICKETING_COORDINATOR","FINANCE_MANAGER");
        Integer newScheduleId = input.scheduleId();
        Integer newBusId = schedules.findBusIdByScheduleId(newScheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a trip with a bus."));

        // Discover existing group booking state via projection queries without pre-loading managed entity
        Integer oldBusId = groups.findAssignedBusIdByGroupId(id).orElse(null);
        Integer oldScheduleId = groups.findScheduleIdByGroupId(id).orElse(null);
        if (oldBusId == null && oldScheduleId != null) {
            oldBusId = schedules.findBusIdByScheduleId(oldScheduleId).orElse(null);
        }

        // Deterministic lock order: Buses ascending -> Schedules ascending -> GroupBooking
        java.util.TreeSet<Integer> busIdsToLock = new java.util.TreeSet<>();
        if (oldBusId != null) busIdsToLock.add(oldBusId);
        busIdsToLock.add(newBusId);

        java.util.TreeSet<Integer> scheduleIdsToLock = new java.util.TreeSet<>();
        if (oldScheduleId != null) scheduleIdsToLock.add(oldScheduleId);
        scheduleIdsToLock.add(newScheduleId);

        if (onAfterGroupConfigDiscoveryHook != null) {
            onAfterGroupConfigDiscoveryHook.run();
        }

        for (Integer bid : busIdsToLock) {
            buses.findByIdForUpdate(bid);
        }

        for (Integer sid : scheduleIdsToLock) {
            schedules.findByIdForUpdate(sid);
        }

        GroupBooking g = require(groups.findByIdForUpdate(id));
        check(g.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED, "Cannot configure a CANCELLED group booking.");
        check(g.getStatus() != GroupBooking.GroupBookingStatus.COMPLETED, "Cannot configure a COMPLETED group booking.");
        eer.syncGroup(g);

        // Revalidate associations under lock
        Integer currentAssignedBusId = (g.getAssignedBus() != null) ? g.getAssignedBus().getId() : null;
        if (currentAssignedBusId != null && !busIdsToLock.contains(currentAssignedBusId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict: assigned bus changed for group booking. Please retry.");
        }
        Integer currentOldScheduleId = (g.getBooking() != null && g.getBooking().getSchedule() != null)
                ? g.getBooking().getSchedule().getId() : null;
        if (currentOldScheduleId != null && !scheduleIdsToLock.contains(currentOldScheduleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict: schedule changed for group booking. Please retry.");
        }

        Schedule sNew = require(schedules.findById(newScheduleId));
        Integer lockedTargetBusId = (sNew.getBus() != null) ? sNew.getBus().getId() : null;
        if (lockedTargetBusId == null || !busIdsToLock.contains(lockedTargetBusId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict: new schedule bus changed. Please retry.");
        }
        check(sNew.getStatus() == Schedule.ScheduleStatus.SCHEDULED && sNew.getDepartureTime().isAfter(LocalDateTime.now()),
                "Select a future scheduled trip with a bus.");

        // Prevent two charters from owning the same schedule
        boolean ownedByOther = groups.findAll().stream().anyMatch(other ->
                other.getBooking() != null && other.getBooking().getSchedule() != null
                && other.getBooking().getSchedule().getId().equals(sNew.getId())
                && !other.getId().equals(g.getId())
                && other.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED);
        check(!ownedByOther, "Cannot assign schedule #" + sNew.getId() + ": It is already assigned to another active group booking.");

        // Prevent assigning a public trip that already has active holds or confirmed passengers
        List<String> activeSeats = reservedSeats.findAllUnavailableSeatsForSchedule(sNew.getId(), LocalDateTime.now());
        check(activeSeats.isEmpty(), "Cannot assign schedule #" + sNew.getId() + " to a group booking: It already has active passenger reservations on seats: " + String.join(", ", activeSeats) + ". Reassign passengers first.");

        // Validate and assign the actual CURRENT locked bus of the target schedule, NEVER the stale pre-lock bus ID
        Bus validatedBus = eer.validateBusAssignmentForCharter(lockedTargetBusId, g.getId(), sNew.getId(), g.getStartDate(), g.getEndDate(), g.getPassengerCount());

        // Reassignment: If reassigning from an old schedule, release old schedule safely only if this charter owned it and no other active charter owns it
        if (currentOldScheduleId != null && !currentOldScheduleId.equals(sNew.getId())) {
            Schedule oldLocked = require(schedules.findById(currentOldScheduleId));
            boolean otherActiveCharterOnOld = groups.findAll().stream().anyMatch(other ->
                    other.getBooking() != null && other.getBooking().getSchedule() != null
                    && other.getBooking().getSchedule().getId().equals(oldLocked.getId())
                    && !other.getId().equals(g.getId())
                    && other.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED);
            if (!otherActiveCharterOnOld) {
                oldLocked.setCharter(false);
                schedules.save(oldLocked);
            }
        }

        // Mark new schedule as exclusive private charter and exclude from public sales
        sNew.setCharter(true);
        schedules.save(sNew);

        g.getBooking().setCustomer(require(customers.findById(input.customerId())));
        g.getBooking().setSchedule(sNew);
        g.getBooking().setBookingType("GROUP");
        g.setAssignedBus(validatedBus);
        g.setEventType(input.eventType());
        bookings.save(g.getBooking());
        return groups.save(g);
    }
    public record GroupPaymentInput(@NotNull Integer groupId, String guestToken, @NotBlank String cardNumber, @NotBlank String cardholderName, @NotBlank String expiry, @NotBlank String cvv) {}
    @PostMapping("/groups/pay-deposit")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> payGroupDeposit(@Valid @RequestBody GroupPaymentInput input) {
        // Deterministic lock order: Bus -> Schedule -> GroupBooking via projection queries
        Integer assignedBusId = groups.findAssignedBusIdByGroupId(input.groupId()).orElse(null);
        Integer scheduleId = groups.findScheduleIdByGroupId(input.groupId()).orElse(null);
        if (assignedBusId == null && scheduleId != null) {
            assignedBusId = schedules.findBusIdByScheduleId(scheduleId).orElse(null);
        }

        if (assignedBusId != null) {
            buses.findByIdForUpdate(assignedBusId);
        }
        if (scheduleId != null) {
            schedules.findByIdForUpdate(scheduleId);
        }

        GroupBooking g = require(groups.findByIdForUpdate(input.groupId()));
        eer.syncGroup(g);

        // Revalidate associations under lock
        Integer currentBus = (g.getAssignedBus() != null) ? g.getAssignedBus().getId() : null;
        if (currentBus != null && !currentBus.equals(assignedBusId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict: assigned bus changed. Please retry.");
        }
        Integer currentSch = (g.getBooking() != null && g.getBooking().getSchedule() != null) ? g.getBooking().getSchedule().getId() : null;
        if (currentSch != null && !currentSch.equals(scheduleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict: schedule changed. Please retry.");
        }

        User currentUser = eer.currentUser();
        // Enforce ownership: Authenticated user must own the group booking, OR be authorized staff. Guest bookings require valid guestAccessToken.
        if (currentUser != null) {
            CustomerProfile customer = customers.findByUserId(currentUser.getId()).orElse(null);
            boolean isOwner = (customer != null && g.getBooking() != null && g.getBooking().getCustomer() != null
                    && customer.getId().equals(g.getBooking().getCustomer().getId()));
            boolean isAuthorizedStaff = staff.findByUserId(currentUser.getId()).map(sp ->
                    Set.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "FINANCE_MANAGER").contains(sp.getStaffType())).orElse(false);
            if (!isOwner && !isAuthorizedStaff) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized: You do not own this group booking.");
            }
        } else {
            // Guest payment requires valid booking-specific authorization token (guestAccessToken bound to booking)
            check(input.guestToken() != null && !input.guestToken().isBlank(), "Unauthorized: Sign in or provide booking verification token to pay deposit.");
            check(g.getGuestAccessToken() != null && g.getGuestAccessToken().equals(input.guestToken().trim()),
                    "Unauthorized: Invalid verification token for guest group booking.");
        }

        check(g.getStatus() == GroupBooking.GroupBookingStatus.APPROVED || g.getStatus() == GroupBooking.GroupBookingStatus.PENDING_REVIEW,
                "Group booking is not in an eligible state for deposit payment. Current status: " + g.getStatus());

        // Validate assigned bus availability under lock before processing deposit
        if (g.getAssignedBus() != null) {
            eer.validateBusAssignmentForCharter(g.getAssignedBus().getId(), g.getId(),
                    g.getBooking() != null && g.getBooking().getSchedule() != null ? g.getBooking().getSchedule().getId() : null,
                    g.getStartDate(), g.getEndDate(), g.getPassengerCount());
        }

        // Check if a successful deposit payment already exists to prevent duplicate payments
        Integer bookingId = g.getBooking() != null ? g.getBooking().getId() : null;
        boolean alreadyPaid = bookingId != null && payments.findAll().stream().anyMatch(pay ->
                pay.getBooking() != null &&
                bookingId.equals(pay.getBooking().getId()) &&
                pay.getStatus() == Payment.PaymentStatus.SUCCESS);
        check(!alreadyPaid, "Deposit payment has already been processed successfully for this group booking.");

        // Authoritative deposit calculation: 30% of totalCost
        BigDecimal authoritativeDeposit = g.getTotalCost() != null ?
                g.getTotalCost().multiply(BigDecimal.valueOf(0.30)).setScale(2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
        check(authoritativeDeposit.compareTo(BigDecimal.ZERO) > 0, "Invalid deposit amount configured for group booking.");

        boolean isSuccess = "4111111111111111".equals(input.cardNumber().replace(" ", "").replace("-", ""))
                && "CIAO TEST".equalsIgnoreCase(input.cardholderName().trim())
                && "12/30".equals(input.expiry().trim())
                && "123".equals(input.cvv().trim());

        String txId = "GRP-SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment p = new Payment();
        p.setBooking(g.getBooking());
        p.setAmount(authoritativeDeposit);
        p.setPaymentMethod(Payment.PaymentMethod.CARD);
        p.setPaymentType("GROUP_DEPOSIT");
        p.setStatus(isSuccess ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED);
        p.setTransactionId(txId);
        p.setCreatedAt(LocalDateTime.now());
        payments.save(p);

        if (isSuccess) {
            // Atomic conditional transition on GroupBooking status
            int updated = groups.atomicTransitionStatus(g.getId(),
                    Set.of(GroupBooking.GroupBookingStatus.APPROVED, GroupBooking.GroupBookingStatus.PENDING_REVIEW),
                    GroupBooking.GroupBookingStatus.DEPOSIT_PAID);
            check(updated > 0, "Concurrent modification detected: Deposit was already recorded by another transaction.");

            g = require(groups.findById(g.getId()));
            g.setDepositAmount(authoritativeDeposit);
            groups.save(g);

            if (g.getBooking() != null) {
                g.getBooking().setBookingStatus("DEPOSIT_PAID");
                bookings.save(g.getBooking());
            }

            if (g.getBooking() != null && g.getBooking().getCustomer() != null && g.getBooking().getCustomer().getUser() != null) {
                Notification n = new Notification();
                n.setUser(g.getBooking().getCustomer().getUser());
                n.setTitle("Group Booking Deposit");
                n.setMessage("[Group Booking Deposit Received] Deposit of LKR " + authoritativeDeposit + " received for Group Booking Ref #GRP-" + g.getId() + ".");
                n.setNotificationType("GROUP_BOOKING");
                n.setSentAt(LocalDateTime.now());
                notifications.save(n);
            }

            return Map.of("status", "SUCCESS", "transactionId", txId, "depositAmount", authoritativeDeposit,
                    "message", "University simulated payment successful. Group deposit marked as PAID.");
        } else {
            return Map.of("status", "FAILED", "transactionId", txId,
                    "message", "Payment simulation failed. Only official university test card details are accepted: 4111111111111111 / CIAO TEST / 12/30 / 123");
        }
    }

    @PostMapping("/groups/pay-balance")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> payGroupBalance(@Valid @RequestBody GroupPaymentInput input) {
        GroupBooking g = require(groups.findByIdForUpdate(input.groupId()));
        eer.syncGroup(g);

        User currentUser = eer.currentUser();
        if (currentUser != null) {
            CustomerProfile customer = customers.findByUserId(currentUser.getId()).orElse(null);
            boolean isOwner = (customer != null && g.getBooking() != null && g.getBooking().getCustomer() != null
                    && customer.getId().equals(g.getBooking().getCustomer().getId()));
            boolean isAuthorizedStaff = staff.findByUserId(currentUser.getId()).map(sp ->
                    Set.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "FINANCE_MANAGER").contains(sp.getStaffType())).orElse(false);
            if (!isOwner && !isAuthorizedStaff) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized: You do not own this group booking.");
            }
        } else {
            check(input.guestToken() != null && !input.guestToken().isBlank(), "Unauthorized: Sign in or provide booking verification token to pay balance.");
            check(g.getGuestAccessToken() != null && g.getGuestAccessToken().equals(input.guestToken().trim()),
                    "Unauthorized: Invalid verification token for guest group booking.");
        }

        check(g.getStatus() == GroupBooking.GroupBookingStatus.DEPOSIT_PAID,
                "Group booking must have its advance deposit paid before settling the remaining balance. Current status: " + g.getStatus());

        BigDecimal total = g.getTotalCost() != null ? g.getTotalCost() : BigDecimal.ZERO;
        BigDecimal deposit = g.getDepositAmount() != null ? g.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal remainingBalance = total.subtract(deposit).setScale(2, RoundingMode.HALF_UP);
        check(remainingBalance.compareTo(BigDecimal.ZERO) > 0, "No remaining balance due for this group booking.");

        boolean isSuccess = "4111111111111111".equals(input.cardNumber().replace(" ", "").replace("-", ""))
                && "CIAO TEST".equalsIgnoreCase(input.cardholderName().trim())
                && "12/30".equals(input.expiry().trim())
                && "123".equals(input.cvv().trim());

        String txId = "GRP-BAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment p = new Payment();
        p.setBooking(g.getBooking());
        p.setAmount(remainingBalance);
        p.setPaymentMethod(Payment.PaymentMethod.CARD);
        p.setPaymentType("GROUP_FINAL_SETTLEMENT");
        p.setStatus(isSuccess ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED);
        p.setTransactionId(txId);
        p.setCreatedAt(LocalDateTime.now());
        payments.save(p);

        if (isSuccess) {
            int updated = groups.atomicTransitionStatus(g.getId(),
                    Set.of(GroupBooking.GroupBookingStatus.DEPOSIT_PAID),
                    GroupBooking.GroupBookingStatus.COMPLETED);
            check(updated > 0, "Concurrent modification detected. Please refresh and retry.");

            g = require(groups.findById(g.getId()));
            if (g.getBooking() != null) {
                g.getBooking().setBookingStatus("COMPLETED");
                bookings.save(g.getBooking());
            }

            if (g.getBooking() != null && g.getBooking().getCustomer() != null && g.getBooking().getCustomer().getUser() != null) {
                Notification n = new Notification();
                n.setUser(g.getBooking().getCustomer().getUser());
                n.setTitle("Group Booking Final Settlement");
                n.setMessage("[Group Booking Fully Settled] Final payment of LKR " + remainingBalance + " received for Group Booking Ref #GRP-" + g.getId() + ". Charter is fully confirmed and complete.");
                n.setNotificationType("GROUP_BOOKING");
                n.setSentAt(LocalDateTime.now());
                notifications.save(n);
            }

            return Map.of("status", "SUCCESS", "transactionId", txId, "balancePaid", remainingBalance,
                    "message", "University simulated payment successful! Remaining balance cleared and charter is marked as COMPLETED.");
        } else {
            return Map.of("status", "FAILED", "transactionId", txId,
                    "message", "Payment simulation failed. Only official university test card details are accepted: 4111111111111111 / CIAO TEST / 12/30 / 123");
        }
    }
    @PostMapping("/payments/{id}/verify") public Payment verify(@PathVariable Integer id) {

        StaffProfile p=permit("FINANCE_MANAGER");Payment payment=require(payments.findById(id));
        check(payment.getStatus()==Payment.PaymentStatus.SUCCESS,"Only successful payments can be verified.");
        if(payment.getVerifiedAt()==null){payment.setVerifiedBy(p);payment.setVerifiedAt(LocalDateTime.now());}
        return payments.save(payment);
    }

    @GetMapping("/financial-report")
    public Map<String, Object> financialReport() {
        permit("FINANCE_MANAGER");
        List<Payment> allPayments = payments.findAll();
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal ticketRevenue = BigDecimal.ZERO;
        BigDecimal groupRevenue = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        int successCount = 0;
        int verifiedCount = 0;
        int failedCount = 0;
        int refundedCount = 0;

        for (Payment p : allPayments) {
            // A REFUNDED payment is the original collection with a changed status,
            // not a second negative transaction. Include it in gross before deducting it once.
            boolean collected = p.getStatus() == Payment.PaymentStatus.SUCCESS
                    || (p.getStatus() == Payment.PaymentStatus.REFUNDED
                        && !"SIMULATED_REFUND".equalsIgnoreCase(p.getPaymentType()));
            if (collected) {
                successCount++;
                if (p.getAmount() != null) {
                    totalCollected = totalCollected.add(p.getAmount());
                    if ("TICKET".equalsIgnoreCase(p.getPaymentType()) || p.getReservation() != null) {
                        ticketRevenue = ticketRevenue.add(p.getAmount());
                    } else {
                        groupRevenue = groupRevenue.add(p.getAmount());
                    }
                }
                if (p.getVerifiedAt() != null) {
                    verifiedCount++;
                }
            }
            if (p.getStatus() == Payment.PaymentStatus.REFUNDED
                    && !"SIMULATED_REFUND".equalsIgnoreCase(p.getPaymentType())) {
                refundedCount++;
                if (p.getAmount() != null) {
                    totalRefunded = totalRefunded.add(p.getAmount());
                    if ("TICKET".equalsIgnoreCase(p.getPaymentType()) || p.getReservation() != null) {
                        ticketRevenue = ticketRevenue.subtract(p.getAmount());
                    } else {
                        groupRevenue = groupRevenue.subtract(p.getAmount());
                    }
                }
            } else if (p.getStatus() == Payment.PaymentStatus.FAILED) {
                failedCount++;
            }
        }

        List<GroupBooking> allGroups = groups.findAll();
        BigDecimal outstandingGroupBalance = BigDecimal.ZERO;
        for (GroupBooking g : allGroups) {
            if (g.getStatus() == GroupBooking.GroupBookingStatus.DEPOSIT_PAID) {
                BigDecimal remaining = (g.getTotalCost() != null ? g.getTotalCost() : BigDecimal.ZERO)
                        .subtract(g.getDepositAmount() != null ? g.getDepositAmount() : BigDecimal.ZERO);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    outstandingGroupBalance = outstandingGroupBalance.add(remaining);
                }
            }
        }

        return Map.ofEntries(
            Map.entry("totalRevenue", totalCollected.subtract(totalRefunded)),
            Map.entry("grossCollected", totalCollected),
            Map.entry("ticketRevenue", ticketRevenue),
            Map.entry("groupRevenue", groupRevenue),
            Map.entry("totalRefunded", totalRefunded),
            Map.entry("refundedTransactions", refundedCount),
            Map.entry("outstandingGroupBalance", outstandingGroupBalance),
            Map.entry("successfulTransactions", successCount),
            Map.entry("verifiedTransactions", verifiedCount),
            Map.entry("failedTransactions", failedCount),
            Map.entry("generatedAt", LocalDateTime.now().toString())
        );
    }
    public record ClaimInput(@NotNull Integer itemId,@NotBlank @Size(max=2000) String proofOfOwnership) {}
    @PostMapping("/claims") public LostItemClaim claim(@Valid @RequestBody ClaimInput input) {
        User u = user();
        LostItem item = require(lostItems.findById(input.itemId()));
        check(item.getStatus() == LostItemStatus.FOUND, "Ownership claims can only be submitted for items currently in FOUND status.");
        
        LostItemClaim c=new LostItemClaim();c.setItem(item);c.setClaimant(u);c.setProofOfOwnership(input.proofOfOwnership());c.setClaimStatus("PENDING");c.setClaimDate(LocalDateTime.now());
        c = claims.save(c);

        // Notify Claimant (Passenger)
        Notification un = new Notification();
        un.setUser(u);
        un.setTitle("Ownership Claim Logged");
        un.setNotificationType("CLAIM_SUBMITTED");
        un.setMessage("[Claim #" + c.getId() + "] Your ownership verification claim for Item #" + item.getId() + " (" + item.getItemDescription() + ") has been submitted for staff review.");
        un.setSentAt(LocalDateTime.now());
        notifications.save(un);

        // Notify Customer Service Supervisors
        List<StaffProfile> supervisors = staff.findByStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        for (StaffProfile sp : supervisors) {
            if (sp.getUser() != null) {
                Notification sn = new Notification();
                sn.setUser(sp.getUser());
                sn.setTitle("New Lost Item Claim");
                sn.setNotificationType("CLAIM_SUBMITTED");
                sn.setMessage("[Action Required] New claim submitted for item: " + item.getItemDescription() + " (Claim #" + c.getId() + ").");
                sn.setSentAt(LocalDateTime.now());
                notifications.save(sn);
            }
        }
        return c;
    }
    @GetMapping("/my-claims") public List<LostItemClaim> myClaims() {
        return claims.findWithDetailsByClaimantId(user().getId());
    }
    public record ClaimDecision(@NotBlank String status) {}
    @PutMapping("/claims/{id}") public LostItemClaim decideClaim(@PathVariable Integer id,@Valid @RequestBody ClaimDecision input) {
        try {
            System.out.println(">>> DECIDE_CLAIM START: id=" + id + ", status=" + (input != null ? input.status() : "null"));
            StaffProfile p = permit("CUSTOMER_SERVICE_SUPERVISOR"); 
            LostItemClaim c = require(claims.findById(id));
            String currentStatus = c.getClaimStatus();
            String next = input.status();
            System.out.println(">>> DECIDE_CLAIM: currentStatus=" + currentStatus + ", next=" + next);
            check((currentStatus.equals("PENDING") && Set.of("APPROVED","REJECTED").contains(next)) || (currentStatus.equals("APPROVED") && next.equals("RETURNED")), "Invalid claim transition.");
            
            LostItem item = c.getItem();
            if (next.equals("APPROVED")) {
                check(item.getStatus() == LostItemStatus.FOUND, "The item must be in FOUND status before a claim can be approved. Current status: " + item.getStatus());
                int itemRows = lostItems.atomicTransitionItem(item.getId(), LostItemStatus.FOUND, LostItemStatus.CLAIMED, p);
                check(itemRows > 0, "Conflict detected: This item was already claimed or approved for another claimant.");
            } else if (next.equals("RETURNED")) {
                check(item.getStatus() == LostItemStatus.CLAIMED || item.getStatus() == LostItemStatus.FOUND, "The item must be in CLAIMED or FOUND status before it can be marked as RETURNED. Current status: " + item.getStatus());
                int itemRows = lostItems.atomicTransitionItem(item.getId(), item.getStatus(), LostItemStatus.RETURNED, p);
                check(itemRows > 0, "Conflict detected: This item was already returned or updated by another transaction.");
            }

            int rows = claims.atomicDecideClaim(id, currentStatus, next, p);
            if (rows == 0) {
                if (next.equals("APPROVED")) {
                    lostItems.atomicTransitionItem(item.getId(), LostItemStatus.CLAIMED, LostItemStatus.FOUND, p);
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict detected: This claim was already updated by another supervisor.");
            }
            
            c = require(claims.findById(id));
            c.setClaimStatus(next);
            c.getItem().setHandledBy(p);
            if (next.equals("APPROVED")) c.getItem().setStatus(LostItemStatus.CLAIMED);
            if (next.equals("RETURNED")) {
                c.setReturnedAt(LocalDateTime.now());
                c.getItem().setStatus(LostItemStatus.RETURNED);
            }
            claims.save(c);
            lostItems.save(c.getItem());
            
            Notification n = new Notification();
            n.setUser(c.getClaimant());
            if (next.equals("RETURNED")) {
                n.setTitle("Lost Property Handed Over & Discharged");
                n.setNotificationType("CLAIM_RETURNED");
                n.setMessage("[Handover Complete] Your claimed property '" + c.getItem().getItemDescription() + "' (Docket LF-#" + String.format("%04d", c.getItem().getId()) + ") has been officially discharged and handed over to you.");
            } else if (next.equals("APPROVED")) {
                n.setTitle("Lost Property Claim Approved");
                n.setNotificationType("CLAIM_APPROVED");
                n.setMessage("[Claim Approved] Your claim for '" + c.getItem().getItemDescription() + "' (Docket LF-#" + String.format("%04d", c.getItem().getId()) + ") has been approved. Please visit the station counter to collect it.");
            } else {
                n.setTitle("Lost Item Claim Update");
                n.setNotificationType("CLAIM_UPDATE");
                n.setMessage("[Lost Item Claim Update] Your lost-item claim #" + id + " has been marked as " + next + ".");
            }
            n.setSentAt(LocalDateTime.now());
            notifications.save(n);
            System.out.println(">>> DECIDE_CLAIM SUCCESS: id=" + id);
            return c;
        } catch (Throwable t) {
            System.err.println(">>> DECIDE_CLAIM EXCEPTION for id=" + id + ": " + t.getClass().getName() + " -> " + t.getMessage());
            t.printStackTrace();
            if (t instanceof ResponseStatusException rse) throw rse;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Claim update failed: " + t.getMessage(), t);
        }
    }
    @GetMapping("/notifications")
    public List<Notification> inbox() {
        return notifications.findByUserIdOrderBySentAtDesc(user().getId());
    }

    @PostMapping("/notifications/{id}/read")
    public Notification read(@PathVariable Integer id) {
        Notification n = require(notifications.findById(id));
        check(n.getUser().getId().equals(user().getId()), "Notification does not belong to you.");
        n.setReadAt(LocalDateTime.now());
        n.setReadStatus(true);
        return notifications.save(n);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Object> readAll() {
        User u = user();
        List<Notification> userNotifs = notifications.findByUserIdOrderBySentAtDesc(u.getId());
        LocalDateTime now = LocalDateTime.now();
        for (Notification n : userNotifs) {
            if (n.getReadAt() == null || Boolean.FALSE.equals(n.getReadStatus())) {
                n.setReadAt(now);
                n.setReadStatus(true);
                notifications.save(n);
            }
        }
        return Map.of("success", true, "updated", userNotifs.size());
    }
    public record ProfileInput(@NotBlank String firstName,@NotBlank String lastName,@NotBlank String address,@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{2,39}") String username,List<@Pattern(regexp="[+0-9 -]{7,20}") String> phoneNumbers){}
    @GetMapping("/profile") public Map<String,Object> profile(){User u=user();eer.profile(u);return Map.of("user",u,"customer",customers.findByUserId(u.getId()).<Object>map(x->x).orElse(Map.of()),"staff",staff.findByUserId(u.getId()).<Object>map(x->x).orElse(Map.of()),"phones",phones.findAll().stream().filter(x->x.getUser().getId().equals(u.getId())).toList());}
    @PutMapping("/profile") public CustomerProfile profile(@Valid @RequestBody ProfileInput input){
        User u=user();CustomerProfile c=eer.customer(u);check(c!=null,"This form is for customer profiles.");
        var duplicate=users.findByUsernameIgnoreCase(input.username());check(duplicate.isEmpty() || duplicate.get().getId().equals(u.getId()),"Username already in use.");
        u.setUsername(input.username());u.setFullName(input.firstName()+" "+input.lastName());users.save(u);
        c.setFirstName(input.firstName());c.setLastName(input.lastName());c.setAddress(input.address());
        phones.deleteAll(phones.findAll().stream().filter(x->x.getUser().getId().equals(u.getId())).toList());phones.flush();
        if(input.phoneNumbers()!=null)for(String number:new HashSet<>(input.phoneNumbers())){UserPhone phone=new UserPhone();phone.setUser(u);phone.setPhoneNumber(number);phones.save(phone);}
        return customers.save(c);
    }
    public record StaffProfileInput(@NotBlank @Size(max=100) String fullName,
                                    @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{2,39}") String username){}
    @PutMapping("/profile/staff") public Map<String,String> updateOwnStaffProfile(@Valid @RequestBody StaffProfileInput input){
        User u=user();
        staff.findByUserId(u.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"Staff account required."));
        String nextUsername=input.username().trim();
        var duplicate=users.findByUsernameIgnoreCase(nextUsername);
        check(duplicate.isEmpty() || duplicate.get().getId().equals(u.getId()),"Username already in use.");
        u.setFullName(input.fullName().trim().replaceAll("\\s+"," "));
        u.setUsername(nextUsername);
        users.save(u);
        return Map.of("fullName",u.getFullName(),"username",u.getUsername());
    }
    public record CheckInInput(@NotBlank String qrCode){}
    @PostMapping("/tickets/check-in") public Map<String,Object> checkIn(@Valid @RequestBody CheckInInput input){
        permit("E_TICKETING_COORDINATOR","BRANCH_MANAGER");
        String rawCode = input.qrCode().trim();
        Ticket t = tickets.findByQrCode(rawCode).orElse(null);
        if (t == null) {
            // Check if input is in format TKT-123 or numeric 123
            String idStr = rawCode.toUpperCase().startsWith("TKT-") ? rawCode.substring(4) : rawCode;
            try {
                int ticketId = Integer.parseInt(idStr);
                t = tickets.findById(ticketId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (t == null && rawCode.toUpperCase().startsWith("RES-")) {
            try {
                int resId = Integer.parseInt(rawCode.substring(4));
                t = tickets.findByReservationId(resId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found with reference or QR code: " + rawCode);
        }
        Reservation r = t.getReservation();
        check(r != null && r.getStatus() == Reservation.ReservationStatus.CONFIRMED, "Ticket is not confirmed.");
        Schedule s=r.getSchedule();
        check(s!=null,"Associated trip schedule not found.");
        check(s.getStatus()!=Schedule.ScheduleStatus.CANCELLED,"Trip has been cancelled. Boarding rejected.");
        check(s.getStatus()!=Schedule.ScheduleStatus.COMPLETED,"Trip has already been completed. Boarding closed.");

        // Enforce valid boarding window: check-in is allowed from 4 hours prior to departure until 1 hour after departure
        if (s.getDepartureTime() != null) {
            LocalDateTime windowStart = s.getDepartureTime().minusHours(4);
            LocalDateTime windowEnd = s.getDepartureTime().plusHours(1);
            LocalDateTime current = LocalDateTime.now();
            if (current.isBefore(windowStart)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in is not open yet. Boarding opens 4 hours before departure at " + windowStart.toLocalTime());
            }
            if (current.isAfter(windowEnd)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in closed: Trip departed over 1 hour ago.");
            }
        }

        LocalDateTime now=LocalDateTime.now();
        int updated = tickets.atomicCheckIn(t.getId(), now);
        check(updated > 0, "Ticket has already been checked in or is currently being processed.");
        t.setCheckedInAt(now);
        return Map.of(
            "ticketId", t.getId(),
            "passenger", r.getPassengerName(),
            "phone", r.getPassengerPhone(),
            "seats", t.getSeatRange() != null ? t.getSeatRange() : "Assigned",
            "route", s.getRoute() != null ? (s.getRoute().getOrigin() + " → " + s.getRoute().getDestination()) : "Route",
            "departureTime", s.getDepartureTime() != null ? s.getDepartureTime().toString() : "",
            "bus", (s.getBus() != null && s.getBus().getPlateNumber() != null) ? s.getBus().getPlateNumber() : "Assigned Bus",
            "checkedInAt", now.toString()
        );
    }

    public record CancelRequestInput(@NotBlank @Size(max=500) String reason) {}

    @PostMapping("/reservations/{id}/cancel-request")
    public Map<String, Object> requestCancellation(@PathVariable Integer id, @Valid @RequestBody CancelRequestInput input) {
        System.out.println(">>> ENTERED requestCancellation: id=" + id + ", reason=" + (input != null ? input.reason() : "null"));
        User u = user();
        System.out.println(">>> user() resolved to: " + (u != null ? u.getEmail() : "null"));
        Reservation r = require(reservations.findByIdForUpdate(id));
        // Guest reservations have no authenticated account owner. They require a
        // separate verified staff-assisted workflow; an ID alone proves nothing.
        boolean isStaffOrAdmin = u.getRole() != null &&
            (u.getRole().getRoleName().contains("ADMIN") || u.getRole().getRoleName().contains("STAFF"));
        if (!isStaffOrAdmin && (r.getUser() == null || !r.getUser().getId().equals(u.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reservation does not belong to you.");
        }
        check(r.getStatus() == Reservation.ReservationStatus.CONFIRMED, "Only confirmed reservations can be submitted for cancellation/refund.");
        Schedule s = r.getSchedule();
        check(s != null && s.getDepartureTime() != null && s.getDepartureTime().isAfter(LocalDateTime.now()), "Cannot cancel reservations for trips that have already departed.");

        // Check if ticket is already checked in
        Optional<Ticket> ticketOpt = tickets.findByReservationId(r.getId());
        if (ticketOpt.isPresent() && ticketOpt.get().getCheckedInAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checked-in tickets cannot be cancelled.");
        }

        // Concurrency and idempotency check: already requested?
        Optional<CancellationRequest> existingPending = cancellationRequests.findPendingByReservationIdForUpdate(r.getId());
        if (existingPending.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancellation request is already pending adjudication for this reservation.");
        }

        CancellationRequest cr = new CancellationRequest(r, u, input.reason());
        cr = cancellationRequests.save(cr);

        // Notify supervisor and user
        Notification n = new Notification();
        n.setUser(u);
        n.setTitle("Cancellation Request Logged");
        n.setNotificationType("REFUND_REQUEST");
        n.setMessage("[Cancellation Request #" + cr.getId() + "] Your cancellation request for booking #" + r.getId() + " (" + input.reason() + ") has been submitted to Customer Service.");
        n.setSentAt(LocalDateTime.now());
        notifications.save(n);

        // Notify Customer Service Supervisors
        List<StaffProfile> supervisors = staff.findByStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        for (StaffProfile sp : supervisors) {
            if (sp.getUser() != null) {
                Notification sn = new Notification();
                sn.setUser(sp.getUser());
                sn.setTitle("New Cancellation Request");
                sn.setNotificationType("REFUND_REQUEST");
                sn.setMessage("[Action Required] Passenger " + r.getPassengerName() + " requested cancellation for booking #" + r.getId() + " (" + input.reason() + ").");
                sn.setSentAt(LocalDateTime.now());
                notifications.save(sn);
            }
        }

        return Map.of(
            "requestId", cr.getId(),
            "reservationId", r.getId(),
            "status", "PENDING",
            "message", "Cancellation request logged successfully. Customer service supervisor will adjudicate."
        );
    }

    @GetMapping("/cancellation-requests")
    public List<Map<String, Object>> getCancellationRequests() {
        permit("CUSTOMER_SERVICE_SUPERVISOR");
        List<CancellationRequest> list = cancellationRequests.findAll();
        List<Map<String, Object>> results = new ArrayList<>();
        for (CancellationRequest cr : list) {
            Reservation r = cr.getReservation();
            Schedule s = r != null ? r.getSchedule() : null;
            Map<String, Object> item = new HashMap<>();
            item.put("id", cr.getId());
            item.put("reservationId", r != null ? r.getId() : 0);
            item.put("passengerName", r != null ? r.getPassengerName() : "N/A");
            item.put("passengerPhone", r != null ? r.getPassengerPhone() : "N/A");
            item.put("totalFare", r != null ? r.getTotalFare() : BigDecimal.ZERO);
            item.put("reason", cr.getReason());
            item.put("status", cr.getStatus().name());
            item.put("route", (s != null && s.getRoute() != null) ? (s.getRoute().getOrigin() + " → " + s.getRoute().getDestination()) : "N/A");
            item.put("departureTime", (s != null && s.getDepartureTime() != null) ? s.getDepartureTime().toString() : "");
            item.put("requestedBy", cr.getRequestedBy() != null ? cr.getRequestedBy().getEmail() : "N/A");
            item.put("adjudicatedBy", cr.getAdjudicatedBy() != null && cr.getAdjudicatedBy().getUser() != null ? cr.getAdjudicatedBy().getUser().getFullName() : "");
            item.put("adjudicationNotes", cr.getAdjudicationNotes() != null ? cr.getAdjudicationNotes() : "");
            results.add(item);
        }
        return results;
    }

    @PostMapping("/reservations/{id}/adjudicate-cancellation")
    public Map<String, Object> adjudicateCancellation(@PathVariable Integer id,
                                                      @RequestParam boolean approve,
                                                      @RequestParam(required = false, defaultValue = "") String notes) {
        StaffProfile supervisor = permit("CUSTOMER_SERVICE_SUPERVISOR");
        
        // Take locks in the same order as requestCancellation: reservation, then request.
        Reservation r = require(reservations.findByIdForUpdate(id));
        CancellationRequest cr = cancellationRequests.findPendingByReservationIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending cancellation request found for this reservation."));
        check(r.getStatus() == Reservation.ReservationStatus.CONFIRMED, "Reservation must be in CONFIRMED status.");

        LocalDateTime now = LocalDateTime.now();
        cr.setAdjudicatedBy(supervisor);
        cr.setAdjudicatedAt(now);
        cr.setAdjudicationNotes(notes);

        if (approve) {
            cr.setStatus(CancellationRequest.RequestStatus.APPROVED);
            cancellationRequests.save(cr);

            r.setStatus(Reservation.ReservationStatus.CANCELLED);
            reservations.save(r);

            // Release seats atomically
            List<ReservedSeat> seatsList = reservedSeats.findByReservationId(r.getId());
            for (ReservedSeat s : seatsList) {
                s.setLockExpiresAt(now);
                reservedSeats.save(s);
            }

            if (r.getBooking() != null) {
                r.getBooking().setBookingStatus("CANCELLED");
                bookings.save(r.getBooking());
            }

            // Refund only money actually collected; an unpaid booking has no reversal.
            List<Payment> paymentList = payments.findByReservationId(r.getId());
            Payment originalPayment = paymentList.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS)
                    .findFirst().orElse(null);

            BigDecimal refundAmount = BigDecimal.ZERO;
            if (originalPayment != null) {
                refundAmount = originalPayment.getAmount();
                originalPayment.setStatus(Payment.PaymentStatus.REFUNDED);
                payments.save(originalPayment);
            }

            if (r.getUser() != null) {
                Notification n = new Notification();
                n.setUser(r.getUser());
                n.setTitle(refundAmount.signum() > 0 ? "Cancellation Approved (Simulated Refund)" : "Cancellation Approved");
                n.setNotificationType(refundAmount.signum() > 0 ? "REFUND_APPROVED" : "CANCELLATION_APPROVED");
                n.setMessage("[Cancellation Approved] Reservation #" + r.getId() + " was cancelled by supervisor "
                        + supervisor.getUser().getFullName() + (refundAmount.signum() > 0
                        ? ". Simulated refund of LKR " + refundAmount + " recorded against demo payment record."
                        : ". No payment had been collected, so no refund was recorded."));
                n.setSentAt(now);
                notifications.save(n);
            }

            return Map.of(
                "requestId", cr.getId(),
                "reservationId", r.getId(),
                "status", "CANCELLED",
                "simulatedRefundAmount", refundAmount,
                "message", refundAmount.signum() > 0 ? "Reservation cancelled and simulated refund recorded." : "Reservation cancelled; no payment was collected, so no refund was recorded."
            );
        } else {
            cr.setStatus(CancellationRequest.RequestStatus.REJECTED);
            cancellationRequests.save(cr);

            if (r.getUser() != null) {
                Notification n = new Notification();
                n.setUser(r.getUser());
                n.setTitle("Cancellation Request Rejected");
                n.setNotificationType("REFUND_REJECTED");
                n.setMessage("[Cancellation Decision] Cancellation request for reservation #" + r.getId() + " was rejected by Customer Service. Note: " + notes);
                n.setSentAt(now);
                notifications.save(n);
            }
            return Map.of(
                "requestId", cr.getId(),
                "reservationId", r.getId(),
                "status", "CONFIRMED",
                "message", "Cancellation request rejected. Booking remains active."
            );
        }
    }
}
