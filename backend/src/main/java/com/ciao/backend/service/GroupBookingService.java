package com.ciao.backend.service;

import com.ciao.backend.dto.groupbooking.GroupBookingRequest;
import com.ciao.backend.dto.groupbooking.GroupBookingResponse;
import com.ciao.backend.dto.groupbooking.GroupBookingStatusUpdateRequest;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.GroupBooking;
import com.ciao.backend.entity.Schedule;
import com.ciao.backend.repository.BusRepository;
import com.ciao.backend.repository.GroupBookingRepository;
import com.ciao.backend.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupBookingService {
    @Autowired private EerService eer;

    @Autowired
    private GroupBookingRepository groupBookingRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private com.ciao.backend.repository.ScheduleRepository scheduleRepository;

    @Autowired
    private com.ciao.backend.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.ciao.backend.repository.StaffProfileRepository staffRepository;

    @Transactional
    public GroupBookingResponse createBookingRequest(GroupBookingRequest request) {
        String name = request.getCustomerName() != null ? request.getCustomerName().trim() : "";
        String phone = request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : "";

        if (name.isEmpty()) {
            throw new RuntimeException("Customer name cannot be empty.");
        }
        if (phone.isEmpty()) {
            throw new RuntimeException("Customer phone cannot be empty.");
        }

        if (request.getStartDate() == null) {
            throw new RuntimeException("Departure start date is required.");
        }
        if (request.getEndDate() == null) {
            throw new RuntimeException("Return end date is required.");
        }
        if (request.getStartDate().toLocalDate().isBefore(java.time.LocalDate.now())) {
            throw new RuntimeException("Departure start date cannot be in the past.");
        }
        if (request.getEndDate().toLocalDate().isBefore(request.getStartDate().toLocalDate())) {
            throw new RuntimeException("Return end date cannot be earlier than departure start date.");
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(request.getStartDate().toLocalDate(), request.getEndDate().toLocalDate()) + 1;
        if (days < 1) days = 1;
        // Server-authoritative quote: base daily charter rate per passenger with minimum floor
        java.math.BigDecimal computedCost = java.math.BigDecimal.valueOf(Math.max(15000, days * request.getPassengerCount() * 1200L));
        java.math.BigDecimal computedDeposit = computedCost.multiply(java.math.BigDecimal.valueOf(0.30)).setScale(2, java.math.RoundingMode.HALF_UP);

        GroupBooking booking = new GroupBooking();
        booking.setCustomerName(name);
        booking.setCustomerPhone(phone);
        booking.setEventType(request.getEventType() == null ? null : request.getEventType().trim());
        booking.setPreferredBusType(request.getPreferredBusType() == null ? null : request.getPreferredBusType().trim());
        booking.setJourneyDetails(request.getJourneyDetails() == null ? null : request.getJourneyDetails().trim());
        booking.setStartDate(request.getStartDate());
        booking.setEndDate(request.getEndDate());
        booking.setPassengerCount(request.getPassengerCount());
        booking.setTotalCost(computedCost);
        booking.setDepositAmount(computedDeposit);
        booking.setStatus(GroupBooking.GroupBookingStatus.PENDING_REVIEW);

        // Secure unpredictable guest access token bound to this booking
        String guestToken = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
        booking.setGuestAccessToken(guestToken);

        GroupBooking saved = groupBookingRepository.save(booking);
        eer.syncGroup(saved);
        GroupBookingResponse response = mapToResponse(saved);
        // Only the initial creation response receives the freshly minted secret guestAccessToken
        response.setGuestAccessToken(saved.getGuestAccessToken());
        return response;
    }

    public List<GroupBookingResponse> getAllGroupBookings() {
        return groupBookingRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public GroupBookingResponse getGroupBookingById(Integer id) {
        GroupBooking booking = groupBookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group Booking not found"));
        return mapToResponse(booking);
    }

    @Transactional
    public GroupBookingResponse updateBookingStatus(Integer id, GroupBookingStatusUpdateRequest request) {
        GroupBooking.GroupBookingStatus newStatus;
        try {
            newStatus = GroupBooking.GroupBookingStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status value");
        }

        // 1. Discover resource IDs via projection queries without loading managed entities into persistence context
        Integer oldBusId = groupBookingRepository.findAssignedBusIdByGroupId(id).orElse(null);
        Integer newBusId = request.getAssignedBusId();
        Integer scheduleId = groupBookingRepository.findScheduleIdByGroupId(id).orElse(null);
        if (oldBusId == null && scheduleId != null) {
            oldBusId = scheduleRepository.findBusIdByScheduleId(scheduleId).orElse(null);
        }

        // 2. Canonical lock order: Buses ascending -> Schedules ascending -> GroupBooking
        java.util.TreeSet<Integer> busIdsToLock = new java.util.TreeSet<>();
        if (oldBusId != null) busIdsToLock.add(oldBusId);
        if (newBusId != null) busIdsToLock.add(newBusId);
        for (Integer bId : busIdsToLock) {
            busRepository.findByIdForUpdate(bId);
        }

        if (scheduleId != null) {
            scheduleRepository.findByIdForUpdate(scheduleId);
        }

        GroupBooking booking = groupBookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Group Booking not found"));

        // 3. Revalidate associations under lock
        Integer actualBusId = booking.getAssignedBus() != null ? booking.getAssignedBus().getId() : null;
        if (actualBusId != null && !busIdsToLock.contains(actualBusId)) {
            throw new RuntimeException("Concurrent conflict: assigned bus changed for group booking. Please retry.");
        }
        Integer actualScheduleId = (booking.getBooking() != null && booking.getBooking().getSchedule() != null)
                ? booking.getBooking().getSchedule().getId() : null;
        if (actualScheduleId != null && (scheduleId == null || !scheduleId.equals(actualScheduleId))) {
            throw new RuntimeException("Concurrent conflict: dedicated schedule changed for group booking. Please retry.");
        }

        GroupBooking.GroupBookingStatus currentStatus = booking.getStatus();
        if (currentStatus == GroupBooking.GroupBookingStatus.CANCELLED && newStatus != GroupBooking.GroupBookingStatus.CANCELLED) {
            throw new RuntimeException("Cannot reactivate a CANCELLED group booking.");
        }
        if (currentStatus == GroupBooking.GroupBookingStatus.COMPLETED && newStatus != GroupBooking.GroupBookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot modify a COMPLETED group booking.");
        }

        // Cancellation should never be blocked by an existing bus conflict
        if (newStatus == GroupBooking.GroupBookingStatus.CANCELLED) {
            booking.setStatus(newStatus);
            if (booking.getBooking() != null) {
                booking.getBooking().setBookingStatus("CANCELLED");
                if (booking.getBooking().getSchedule() != null) {
                    Schedule sch = booking.getBooking().getSchedule();
                    // Do not reopen a schedule owned by another active charter
                    boolean otherActiveCharterOnSchedule = groupBookingRepository.findAll().stream().anyMatch(other ->
                            !other.getId().equals(booking.getId()) &&
                            other.getBooking() != null && other.getBooking().getSchedule() != null
                            && other.getBooking().getSchedule().getId().equals(sch.getId())
                            && other.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED);
                    if (!otherActiveCharterOnSchedule) {
                        sch.setCharter(false);
                        scheduleRepository.save(sch);
                    }
                }
            }
            GroupBooking cancelled = groupBookingRepository.save(booking);
            eer.syncGroup(cancelled);
            return mapToResponse(cancelled);
        }

        // Determine effective assigned bus
        Integer busToValidate = request.getAssignedBusId() != null
                ? request.getAssignedBusId()
                : (booking.getAssignedBus() != null ? booking.getAssignedBus().getId() : null);

        if (newStatus == GroupBooking.GroupBookingStatus.APPROVED || newStatus == GroupBooking.GroupBookingStatus.DEPOSIT_PAID) {
            if (busToValidate == null) {
                throw new RuntimeException("A bus must be assigned before approving a group booking or recording its deposit.");
            }
        }

        Integer ownDedicatedScheduleId = (booking.getBooking() != null && booking.getBooking().getSchedule() != null)
                ? booking.getBooking().getSchedule().getId()
                : null;

        if (busToValidate != null) {
            // Exclude own dedicated schedule so group trip does not conflict with itself
            Bus bus = eer.validateBusAssignmentForCharter(busToValidate, booking.getId(), ownDedicatedScheduleId,
                    booking.getStartDate(), booking.getEndDate(), booking.getPassengerCount());
            booking.setAssignedBus(bus);

            // Keep parent booking's dedicated schedule in sync if bus reassignment occurs
            if (booking.getBooking() != null && booking.getBooking().getSchedule() != null) {
                Schedule assignedSchedule = booking.getBooking().getSchedule();
                if (assignedSchedule.getBus() == null || !assignedSchedule.getBus().getId().equals(bus.getId())) {
                    assignedSchedule.setBus(bus);
                    scheduleRepository.save(assignedSchedule);
                }
            }
        }

        // DEPOSIT_PAID must be backed by a verified payment
        if (newStatus == GroupBooking.GroupBookingStatus.DEPOSIT_PAID) {
            boolean hasPaidDeposit = paymentRepository.findAll().stream().anyMatch(p ->
                    p.getBooking() != null && booking.getBooking() != null &&
                    p.getBooking().getId().equals(booking.getBooking().getId()) &&
                    p.getStatus() == com.ciao.backend.entity.Payment.PaymentStatus.SUCCESS);
            if (!hasPaidDeposit) {
                throw new RuntimeException("Cannot mark DEPOSIT_PAID without a recorded successful payment. Complete test card payment first.");
            }
        }

        booking.setStatus(newStatus);
        if (booking.getBooking() != null) {
            booking.getBooking().setBookingStatus(newStatus.name());
        }
        GroupBooking updated = groupBookingRepository.save(booking);
        eer.syncGroup(updated);
        return mapToResponse(updated);
    }

    private GroupBookingResponse mapToResponse(GroupBooking booking) {
        GroupBookingResponse res = new GroupBookingResponse();
        res.setId(booking.getId());
        res.setBookingReference("GRP-" + booking.getId());
        res.setCustomerName(booking.getCustomerName());
        res.setCustomerPhone(booking.getCustomerPhone());
        res.setEventType(booking.getEventType());
        res.setPreferredBusType(booking.getPreferredBusType());
        res.setJourneyDetails(booking.getJourneyDetails());
        res.setStartDate(booking.getStartDate());
        res.setEndDate(booking.getEndDate());
        res.setPassengerCount(booking.getPassengerCount());
        res.setTotalCost(booking.getTotalCost());
        res.setDepositAmount(booking.getDepositAmount());
        res.setStatus(booking.getStatus().name());
        res.setCreatedAt(booking.getCreatedAt());
        if (booking.getAssignedBus() != null) {
            res.setAssignedBusId(booking.getAssignedBus().getId());
        }
        // Secret guestAccessToken is strictly withheld from general list, detail, and status responses
        res.setGuestAccessToken(null);
        return res;
    }

    public GroupBookingResponse getCustomerStatus(String reference, String phone) {
        if (reference == null || phone == null || !reference.matches("(?i)GRP-[0-9]+")) {
            throw new RuntimeException("Invalid booking reference or phone number");
        }
        Integer id = Integer.valueOf(reference.substring(4));
        GroupBooking booking = groupBookingRepository.findByIdAndCustomerPhone(id, phone.trim())
                .orElseThrow(() -> new RuntimeException("Booking not found. Check the reference and phone number."));
        return mapToResponse(booking);
    }

    @Transactional
    public String recoverGuestAccessToken(String reference, String phone, String name) {
        // Enforce staff authorization: In the absence of an SMS/email verification gateway,
        // guest secret tokens may ONLY be recovered via authorized staff-assisted recovery.
        com.ciao.backend.entity.User currentUser = eer.currentUser();
        if (currentUser == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Unauthorized: Guest token recovery requires an authorized staff-assisted workflow."
            );
        }
        boolean isAuthorizedStaff = staffRepository.findByUserId(currentUser.getId()).map(sp ->
                java.util.Set.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "E_TICKETING_COORDINATOR").contains(sp.getStaffType())
        ).orElse(false);

        if (!isAuthorizedStaff) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Forbidden: Only authorized staff (E-Ticketing Coordinator, Operations Manager, System Admin) can recover guest tokens."
            );
        }

        if (reference == null || phone == null || !reference.matches("(?i)GRP-[0-9]+")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid booking reference or phone number."
            );
        }
        Integer id = Integer.valueOf(reference.substring(4));
        GroupBooking booking = groupBookingRepository.findByIdAndCustomerPhone(id, phone.trim())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Booking not found. Check reference and contact phone."
                ));

        if (name != null && !name.trim().equalsIgnoreCase(booking.getCustomerName().trim())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Customer name does not match booking records."
            );
        }

        if (booking.getGuestAccessToken() == null || booking.getGuestAccessToken().isBlank()) {
            String token = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
            booking.setGuestAccessToken(token);
            groupBookingRepository.save(booking);
        }
        return booking.getGuestAccessToken();
    }
}
