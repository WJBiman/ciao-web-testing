package com.ciao.backend.service;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class EerService {
    @Autowired CustomerProfileRepository customers;
    @Autowired StaffProfileRepository staff;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired ReservationRepository reservations;
    @Autowired GroupBookingRepository groups;
    @Autowired PaymentRepository payments;
    @Autowired TicketRepository tickets;
    @Autowired BusSeatRepository seats;
    @Autowired ReservedSeatRepository allocations;
    @Autowired NotificationRepository notifications;
    @Autowired BusRepository busRepository;
    @Autowired ScheduleRepository scheduleRepository;

    public Bus validateBusAssignmentForCharter(Integer busId, Integer groupBookingId, LocalDateTime start, LocalDateTime end, int passengerCount) {
        return validateBusAssignmentForCharter(busId, groupBookingId, null, start, end, passengerCount);
    }

    public Bus validateBusAssignmentForCharter(Integer busId, Integer groupBookingId, Integer excludedScheduleId, LocalDateTime start, LocalDateTime end, int passengerCount) {
        if (busId == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bus ID is required.");
        }
        if (end.isBefore(start)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Group booking end date must be after start date.");
        }

        // Lock shared bus resource so concurrent requests cannot double-assign
        Bus bus = busRepository.findByIdForUpdate(busId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bus not found with ID: " + busId));

        if (bus.getStatus() == Bus.BusStatus.RETIRED || bus.getStatus() == Bus.BusStatus.MAINTENANCE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Bus " + bus.getPlateNumber() + " is in " + bus.getStatus() + " status and cannot be assigned to a group booking.");
        }

        if (passengerCount > bus.getCapacity()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Passenger count (" + passengerCount + ") exceeds bus capacity (" + bus.getCapacity() + ").");
        }

        // 1. Check conflicts with materialized public trips (excluding any dedicated schedule for this charter)
        if (excludedScheduleId != null) {
            Schedule excludedSchedule = scheduleRepository.findById(excludedScheduleId)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Excluded schedule not found with ID: " + excludedScheduleId));
            // If group booking ID is provided, verify legitimacy of exclusion
            if (groupBookingId != null) {
                GroupBooking gb = groups.findById(groupBookingId).orElse(null);
                boolean isCurrentDedicated = (gb != null && gb.getBooking() != null && gb.getBooking().getSchedule() != null
                        && gb.getBooking().getSchedule().getId().equals(excludedScheduleId));
                if (!isCurrentDedicated) {
                    // Check if it belongs to another active charter
                    boolean assignedToAnotherGroup = groups.findAll().stream().anyMatch(other ->
                            !other.getId().equals(groupBookingId) &&
                            other.getBooking() != null && other.getBooking().getSchedule() != null
                            && other.getBooking().getSchedule().getId().equals(excludedScheduleId)
                            && other.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED);
                    if (assignedToAnotherGroup) {
                        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                                "Cannot exclude schedule #" + excludedScheduleId + " for group booking #" + groupBookingId + ": It is assigned to another group booking.");
                    }
                }
            }

            // Verify schedule has no active passenger reservations
            List<String> activeSeats = allocations.findAllUnavailableSeatsForSchedule(excludedScheduleId, LocalDateTime.now());
            if (!activeSeats.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Cannot exclude schedule #" + excludedScheduleId + " from conflict checks: It has active passenger reservations on seats: " + String.join(", ", activeSeats) + ".");
            }

            // Verify schedule is not assigned to another active charter
            boolean ownedByOther = groups.findAll().stream().anyMatch(other ->
                    other.getBooking() != null && other.getBooking().getSchedule() != null
                    && other.getBooking().getSchedule().getId().equals(excludedScheduleId)
                    && (groupBookingId == null || !other.getId().equals(groupBookingId))
                    && other.getStatus() != GroupBooking.GroupBookingStatus.CANCELLED);
            if (ownedByOther) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Cannot exclude schedule #" + excludedScheduleId + " from conflict checks: It is assigned to another group booking.");
            }
        }

        List<Schedule> publicConflicts = (excludedScheduleId != null)
                ? scheduleRepository.findBusConflictsExcluding(busId, excludedScheduleId, start, end)
                : scheduleRepository.findBusConflicts(busId, start, end);
        if (!publicConflicts.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Bus " + bus.getPlateNumber() + " is already assigned to an active route trip during the requested dates.");
        }

        // 2. Check conflicts with unmaterialized daily recurring public departures (including previous-day overnight departures)
        List<Schedule> recurringTemplates = scheduleRepository.findRecurringTemplatesForBus(busId);
        for (Schedule tmpl : recurringTemplates) {
            if (excludedScheduleId != null && tmpl.getId().equals(excludedScheduleId)) continue;
            Duration tripDuration = Duration.between(tmpl.getDepartureTime(), tmpl.getArrivalTime());
            LocalDate tmplStart = tmpl.getDepartureTime().toLocalDate();
            // Start checking from the earliest date whose trip duration could overlap the start of this charter
            LocalDate minDay = start.minus(tripDuration).toLocalDate();
            if (minDay.isBefore(tmplStart)) minDay = tmplStart;
            LocalDate maxDay = end.toLocalDate();
            if (tmpl.getRepeatUntil() != null && maxDay.isAfter(tmpl.getRepeatUntil())) maxDay = tmpl.getRepeatUntil();

            for (LocalDate d = minDay; !d.isAfter(maxDay); d = d.plusDays(1)) {
                LocalDateTime dep = d.atTime(tmpl.getDepartureTime().toLocalTime());
                LocalDateTime arr = dep.plus(tripDuration);
                if (dep.isBefore(end) && arr.isAfter(start)) {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Bus " + bus.getPlateNumber() + " has a scheduled daily service on " + d + " during the requested group booking period.");
                }
            }
        }

        // 3. Check conflicts with other active approved/deposit-paid charters
        List<GroupBooking> charterConflicts = groups.findOverlappingGroupBookings(busId, groupBookingId != null ? groupBookingId : -1, start, end);
        if (!charterConflicts.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Bus " + bus.getPlateNumber() + " is already assigned to another group booking during this time.");
        }

        return bus;
    }

    public Optional<String> checkBusConflictForPublicTrip(Integer busId, Integer scheduleId, Integer recurrenceParentId, LocalDateTime departureTime, LocalDateTime arrivalTime, boolean repeatDaily, LocalDate repeatUntil) {
        if (busId == null) return Optional.empty();
        if (!arrivalTime.isAfter(departureTime)) {
            return Optional.of("Schedule arrival time must be after departure time.");
        }

        Optional<Bus> busOpt = busRepository.findById(busId);
        if (busOpt.isEmpty()) {
            return Optional.of("Bus not found with ID: " + busId);
        }
        Bus bus = busOpt.get();
        if (bus.getStatus() == Bus.BusStatus.RETIRED || bus.getStatus() == Bus.BusStatus.MAINTENANCE) {
            return Optional.of("Bus " + bus.getPlateNumber() + " is in " + bus.getStatus() + " status and cannot be assigned.");
        }

        Duration tripDuration = Duration.between(departureTime, arrivalTime);

        if (!repeatDaily) {
            // Check public trip conflicts excluding the schedule and its recurrence parent
            List<Schedule> pubConflicts = scheduleRepository.findBusConflictsExcluding(busId, scheduleId != null ? scheduleId : -1, departureTime, arrivalTime);
            for (Schedule conflict : pubConflicts) {
                if (recurrenceParentId != null) {
                    if (conflict.getId().equals(recurrenceParentId)) {
                        continue; // Exclude own abstract template
                    }
                    if (conflict.getRecurrenceParentId() != null
                            && conflict.getRecurrenceParentId().equals(recurrenceParentId)
                            && conflict.getDepartureTime().equals(departureTime)) {
                        continue; // Exclude the already-materialized occurrence for this exact departure
                    }
                }
                return Optional.of("Bus " + bus.getPlateNumber() + " has a conflicting public trip: " + conflict.getDepartureTime() + " - " + conflict.getArrivalTime());
            }

            // Check unmaterialized recurring templates
            List<Schedule> recurringTemplates = scheduleRepository.findRecurringTemplatesForBus(busId);
            for (Schedule tmpl : recurringTemplates) {
                if (scheduleId != null && tmpl.getId().equals(scheduleId)) continue;
                if (recurrenceParentId != null && tmpl.getId().equals(recurrenceParentId)) continue;

                Duration tmplDuration = Duration.between(tmpl.getDepartureTime(), tmpl.getArrivalTime());
                LocalDate tmplStart = tmpl.getDepartureTime().toLocalDate();
                LocalDate minDay = departureTime.minus(tmplDuration).toLocalDate();
                if (minDay.isBefore(tmplStart)) minDay = tmplStart;
                LocalDate maxDay = arrivalTime.toLocalDate();
                if (tmpl.getRepeatUntil() != null && maxDay.isAfter(tmpl.getRepeatUntil())) maxDay = tmpl.getRepeatUntil();

                for (LocalDate d = minDay; !d.isAfter(maxDay); d = d.plusDays(1)) {
                    LocalDateTime dep = d.atTime(tmpl.getDepartureTime().toLocalTime());
                    LocalDateTime arr = dep.plus(tmplDuration);
                    if (dep.isBefore(arrivalTime) && arr.isAfter(departureTime)) {
                        return Optional.of("Bus " + bus.getPlateNumber() + " has an overlapping daily service schedule on " + d + ".");
                    }
                }
            }

            // Check conflicting charters
            List<GroupBooking> charterConflicts = groups.findOverlappingGroupBookings(busId, -1, departureTime, arrivalTime);
            if (!charterConflicts.isEmpty()) {
                return Optional.of("Bus " + bus.getPlateNumber() + " is assigned to a group booking and is unavailable for individual bookings.");
            }
        } else {
            // Recurring service:
            // 0. A daily service cannot have a duration >= 24 hours, otherwise its own daily occurrences overlap with each other
            if (!tripDuration.minus(Duration.ofHours(24)).isNegative()) {
                return Optional.of("A daily recurring service trip duration (" + tripDuration.toHours() + "h) cannot exceed 24 hours as it conflicts with its own next occurrence.");
            }

            LocalDate serviceStart = departureTime.toLocalDate();
            LocalDate serviceEnd = repeatUntil != null ? repeatUntil : LocalDate.MAX;

            // 1. Materialized conflicts:
            // A materialized trip m on date d overlaps this recurring service if:
            // d is within the recurring service active window [serviceStart, serviceEnd]
            // AND the candidate occurrence on date d (or adjacent date for overnight duration) overlaps m.
            List<Schedule> materialized = scheduleRepository.findByBusId(busId).stream()
                    .filter(s -> s.getStatus() == Schedule.ScheduleStatus.SCHEDULED)
                    .filter(s -> scheduleId == null || !s.getId().equals(scheduleId))
                    .toList();
            for (Schedule m : materialized) {
                LocalDate mDay = m.getDepartureTime().toLocalDate();
                // Check occurrence starting on mDay (if mDay is within service window)
                if (!mDay.isBefore(serviceStart) && !mDay.isAfter(serviceEnd)) {
                    LocalDateTime candidateDep = mDay.atTime(departureTime.toLocalTime());
                    LocalDateTime candidateArr = candidateDep.plus(tripDuration);
                    if (candidateDep.isBefore(m.getArrivalTime()) && candidateArr.isAfter(m.getDepartureTime())) {
                        return Optional.of("Bus " + bus.getPlateNumber() + " has a conflicting public trip on " + mDay + ".");
                    }
                }
                // Also check if candidate occurrence on prevDay extends overnight into m
                LocalDate prevDay = mDay.minusDays(1);
                if (!prevDay.isBefore(serviceStart) && !prevDay.isAfter(serviceEnd)) {
                    LocalDateTime candidateDep = prevDay.atTime(departureTime.toLocalTime());
                    LocalDateTime candidateArr = candidateDep.plus(tripDuration);
                    if (candidateDep.isBefore(m.getArrivalTime()) && candidateArr.isAfter(m.getDepartureTime())) {
                        return Optional.of("Bus " + bus.getPlateNumber() + " has a conflicting overnight public trip extending into " + mDay + ".");
                    }
                }
                // Also check if materialized trip m departs on mDay overnight and overlaps candidate on nextDay
                LocalDate nextDay = mDay.plusDays(1);
                if (!nextDay.isBefore(serviceStart) && !nextDay.isAfter(serviceEnd)) {
                    LocalDateTime candidateDep = nextDay.atTime(departureTime.toLocalTime());
                    LocalDateTime candidateArr = candidateDep.plus(tripDuration);
                    if (candidateDep.isBefore(m.getArrivalTime()) && candidateArr.isAfter(m.getDepartureTime())) {
                        return Optional.of("Bus " + bus.getPlateNumber() + " has a conflicting public trip with overnight journey from " + mDay + ".");
                    }
                }
            }

            // 2. Check recurring-versus-recurring conflicts:
            // Two recurring series conflict if their active date ranges intersect [overlapStart, overlapEnd]
            // AND their daily departure windows overlap (accounting for same-day and cross-midnight).
            List<Schedule> templates = scheduleRepository.findRecurringTemplatesForBus(busId);
            for (Schedule tmpl : templates) {
                if (scheduleId != null && tmpl.getId().equals(scheduleId)) continue;
                Duration otherDuration = Duration.between(tmpl.getDepartureTime(), tmpl.getArrivalTime());
                LocalDate otherStart = tmpl.getDepartureTime().toLocalDate();
                LocalDate otherEnd = tmpl.getRepeatUntil() != null ? tmpl.getRepeatUntil() : LocalDate.MAX;

                LocalDate overlapStart = serviceStart.isAfter(otherStart) ? serviceStart : otherStart;
                LocalDate overlapEnd = serviceEnd.isBefore(otherEnd) ? serviceEnd : otherEnd;

                if (!overlapStart.isAfter(overlapEnd)) {
                    // Their active date ranges intersect! Check daily recurring pattern at start and end of overlap
                    for (LocalDate testDay : List.of(overlapStart, overlapEnd)) {
                        LocalDateTime dep1 = testDay.atTime(departureTime.toLocalTime());
                        LocalDateTime arr1 = dep1.plus(tripDuration);

                        for (int dayOffset = -1; dayOffset <= 1; dayOffset++) {
                            LocalDate otherDay = testDay.plusDays(dayOffset);
                            if (!otherDay.isBefore(otherStart) && !otherDay.isAfter(otherEnd)) {
                                LocalDateTime dep2 = otherDay.atTime(tmpl.getDepartureTime().toLocalTime());
                                LocalDateTime arr2 = dep2.plus(otherDuration);
                                if (dep1.isBefore(arr2) && arr1.isAfter(dep2)) {
                                    return Optional.of("Bus " + bus.getPlateNumber() + " has an overlapping daily service schedule with template #" + tmpl.getId() + ".");
                                }
                            }
                        }
                    }
                } else {
                    // Departure date ranges do not intersect, but check if an overnight journey interval crosses the boundary
                    if (serviceEnd.plusDays(1).isEqual(otherStart)) {
                        LocalDateTime dep1 = serviceEnd.atTime(departureTime.toLocalTime());
                        LocalDateTime arr1 = dep1.plus(tripDuration);
                        LocalDateTime dep2 = otherStart.atTime(tmpl.getDepartureTime().toLocalTime());
                        LocalDateTime arr2 = dep2.plus(otherDuration);
                        if (dep1.isBefore(arr2) && arr1.isAfter(dep2)) {
                            return Optional.of("Bus " + bus.getPlateNumber() + " has an overlapping overnight daily service schedule with template #" + tmpl.getId() + ".");
                        }
                    } else if (otherEnd.plusDays(1).isEqual(serviceStart)) {
                        LocalDateTime dep1 = serviceStart.atTime(departureTime.toLocalTime());
                        LocalDateTime arr1 = dep1.plus(tripDuration);
                        LocalDateTime dep2 = otherEnd.atTime(tmpl.getDepartureTime().toLocalTime());
                        LocalDateTime arr2 = dep2.plus(otherDuration);
                        if (dep1.isBefore(arr2) && arr1.isAfter(dep2)) {
                            return Optional.of("Bus " + bus.getPlateNumber() + " has an overlapping overnight daily service schedule with template #" + tmpl.getId() + ".");
                        }
                    }
                }
            }

            // 3. Check conflicting charters:
            // A charter overlaps if its start/end window intersects the recurring service active date range.
            List<GroupBooking> allCharters = groups.findAll().stream()
                    .filter(g -> g.getAssignedBus() != null && g.getAssignedBus().getId().equals(busId))
                    .filter(g -> g.getStatus() == GroupBooking.GroupBookingStatus.APPROVED || g.getStatus() == GroupBooking.GroupBookingStatus.DEPOSIT_PAID)
                    .toList();
            for (GroupBooking charter : allCharters) {
                LocalDate cStart = charter.getStartDate().toLocalDate();
                LocalDate cEnd = charter.getEndDate().toLocalDate();
                if (!cEnd.isBefore(serviceStart) && !cStart.isAfter(serviceEnd)) {
                    // Check occurrences starting from max(serviceStart, cStart - 1) to min(serviceEnd, cEnd)
                    LocalDate checkStart = cStart.minusDays(1).isBefore(serviceStart) ? serviceStart : cStart.minusDays(1);
                    LocalDate checkEnd = cEnd.isAfter(serviceEnd) ? serviceEnd : cEnd;
                    for (LocalDate d = checkStart; !d.isAfter(checkEnd); d = d.plusDays(1)) {
                        LocalDateTime dep = d.atTime(departureTime.toLocalTime());
                        LocalDateTime arr = dep.plus(tripDuration);
                        if (dep.isBefore(charter.getEndDate()) && arr.isAfter(charter.getStartDate())) {
                            return Optional.of("Bus " + bus.getPlateNumber() + " is assigned to a group booking from " + charter.getStartDate() + " to " + charter.getEndDate() + ".");
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public Bus validateBusAvailableForPublicTrip(Integer busId, Integer scheduleId, LocalDateTime departureTime, LocalDateTime arrivalTime, boolean repeatDaily, LocalDate repeatUntil) {
        return validateBusAvailableForPublicTrip(busId, scheduleId, null, departureTime, arrivalTime, repeatDaily, repeatUntil);
    }

    public Bus validateBusAvailableForPublicTrip(Integer busId, Integer scheduleId, Integer recurrenceParentId, LocalDateTime departureTime, LocalDateTime arrivalTime, boolean repeatDaily, LocalDate repeatUntil) {
        if (busId == null) return null;
        // Lock shared bus resource
        Bus bus = busRepository.findByIdForUpdate(busId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bus not found with ID: " + busId));

        Optional<String> conflict = checkBusConflictForPublicTrip(busId, scheduleId, recurrenceParentId, departureTime, arrivalTime, repeatDaily, repeatUntil);
        if (conflict.isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, conflict.get());
        }
        return bus;
    }

    public boolean hasActiveReservationsExceedingCapacity(Integer busId, int newCapacity) {
        // 1. Check all active allocations (both confirmed booked seats and unexpired locked seat holds)
        List<ReservedSeat> activeAllocations = allocations.findActiveAllocationsForBus(busId, LocalDateTime.now());
        for (ReservedSeat alloc : activeAllocations) {
            try {
                int seatNum = Integer.parseInt(alloc.getSeatNumber().replaceAll("[^0-9]", ""));
                if (seatNum > newCapacity) return true;
            } catch (NumberFormatException ignored) {}
        }

        // 2. Check assigned group charters exceeding proposed capacity
        boolean charterExceeds = groups.findAll().stream().anyMatch(g ->
                g.getAssignedBus() != null && g.getAssignedBus().getId().equals(busId) &&
                g.getEndDate().isAfter(LocalDateTime.now()) &&
                (g.getStatus() == GroupBooking.GroupBookingStatus.APPROVED || g.getStatus() == GroupBooking.GroupBookingStatus.DEPOSIT_PAID) &&
                g.getPassengerCount() > newCapacity);
        if (charterExceeds) return true;

        return false;
    }

    public User currentUser() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        if (auth.getPrincipal() instanceof com.ciao.backend.security.UserDetailsImpl details) {
            if (details.getId() != null) {
                Optional<User> found = users.findById(details.getId());
                if (found.isPresent()) return found.get();
            }
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) return null;
        return users.findByEmailIgnoreCase(name)
                .or(() -> users.findByUsernameIgnoreCase(name))
                .or(() -> users.findByPhone(name))
                .or(() -> {
                    String norm = com.ciao.backend.security.AccountIdentifiers.normalize(name);
                    var matches = users.findByPhoneIn(com.ciao.backend.security.AccountIdentifiers.phoneForms(norm));
                    return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
                })
                .orElse(null);
    }

    public CustomerProfile customer(User user) {
        if (user == null || !"PASSENGER".equals(user.getRole().getRoleName().replace("ROLE_", ""))) return null;
        return customers.findByUserId(user.getId()).orElseGet(() -> {
            CustomerProfile value = new CustomerProfile();
            value.setUser(user);
            String[] name = user.getFullName().trim().split("\\s+", 2);
            value.setFirstName(name[0]); value.setLastName(name.length > 1 ? name[1] : "");
            value.setRegisteredDate(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now());
            return customers.save(value);
        });
    }

    public void profile(User user) {
        if (customer(user) != null) return;
        if (staff.findByUserId(user.getId()).isEmpty()) {
            StaffProfile value = new StaffProfile(); value.setUser(user);
            String rawRole = user.getRole().getRoleName().replace("ROLE_", "");
            List<String> eerRoles = List.of("SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "BRANCH_MANAGER", "FINANCE_MANAGER", "E_TICKETING_COORDINATOR", "CUSTOMER_SERVICE_SUPERVISOR");
            if (eerRoles.contains(rawRole)) {
                value.setStaffType(rawRole);
            } else {
                value.setStaffType(rawRole.contains("ADMIN") ? "SYSTEM_ADMINISTRATOR" : "UNASSIGNED");
            }
            staff.save(value);
        }
    }

    public void syncSeats(Bus bus) {
        if (bus == null || bus.getCapacity() == null) return;
        List<BusSeat> existingSeats = seats.findByBusId(bus.getId());
        Map<String, BusSeat> existingMap = new HashMap<>();
        existingSeats.forEach(seat -> existingMap.put(seat.getSeatNumber(), seat));

        for (int i = 1; i <= bus.getCapacity(); i++) {
            String seatNum = String.valueOf(i);
            if (!existingMap.containsKey(seatNum)) {
                BusSeat seat = new BusSeat();
                seat.setBus(bus);
                seat.setSeatNumber(seatNum);
                seat.setSeatStatus("ACTIVE");
                seats.save(seat);
            } else {
                BusSeat seat = existingMap.get(seatNum);
                if (!"ACTIVE".equals(seat.getSeatStatus())) {
                    seat.setSeatStatus("ACTIVE");
                    seats.save(seat);
                }
            }
        }
        // Handle reduction: mark physical seats exceeding new capacity as INACTIVE (preserves historical references)
        for (BusSeat seat : existingSeats) {
            try {
                int seatNum = Integer.parseInt(seat.getSeatNumber().replaceAll("[^0-9]", ""));
                if (seatNum > bus.getCapacity() && !"INACTIVE".equals(seat.getSeatStatus())) {
                    seat.setSeatStatus("INACTIVE");
                    seats.save(seat);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    public Booking syncReservation(Reservation reservation) {
        Booking booking = reservation.getBooking();
        if (booking == null) {
            booking = new Booking(); booking.setBookingType("INDIVIDUAL");
            booking.setBookingDate(reservation.getCreatedAt() != null ? reservation.getCreatedAt() : LocalDateTime.now());
        }
        booking.setCustomer(customer(reservation.getUser())); booking.setSchedule(reservation.getSchedule());
        booking.setBookingStatus(reservation.getStatus().name()); booking.setTotalFare(reservation.getTotalFare());
        booking = bookings.save(booking); reservation.setBooking(booking); reservations.save(reservation);
        Bus bus = reservation.getSchedule().getBus(); syncSeats(bus);
        for (ReservedSeat allocation : allocations.findByReservationId(reservation.getId())) {
            if (bus != null) {
                allocation.setSeat(seats.findByBusIdAndSeatNumber(bus.getId(), allocation.getSeatNumber()).orElse(null));
                allocations.save(allocation);
            }
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED && tickets.findByReservationId(reservation.getId()).isEmpty()) {
            Ticket ticket = new Ticket(); ticket.setReservation(reservation); ticket.setIssueDate(LocalDateTime.now());
            ticket.setQrCode("CIAO-TICKET:" + UUID.randomUUID());
            ticket.setSeatRange(String.join(", ", allocations.findByReservationId(reservation.getId()).stream().map(ReservedSeat::getSeatNumber).toList()));
            tickets.save(ticket);
        }
        return booking;
    }

    public void syncGroup(GroupBooking group) {
        Booking booking = group.getBooking();
        if (booking == null) {
            booking = new Booking(); booking.setBookingType("GROUP"); booking.setBookingDate(LocalDateTime.now());
            booking.setCustomer(customer(currentUser()));
        }
        booking.setBookingStatus(group.getStatus().name()); booking.setTotalFare(group.getTotalCost());
        group.setBooking(bookings.save(booking)); groups.save(group);
    }

    public void recordPayment(Payment payment) {
        if (payment.getReservation() != null) payment.setBooking(syncReservation(payment.getReservation()));
        if (payment.getPaymentType() == null) payment.setPaymentType("TICKET");
        payments.save(payment);
    }

    public void notifyBooking(Reservation reservation, String message) {
        Booking booking = syncReservation(reservation);
        if (reservation.getUser() == null) return;
        Notification n = new Notification(); n.setUser(reservation.getUser()); n.setBooking(booking);
        n.setTitle("Booking Update");
        n.setNotificationType("BOOKING_UPDATE"); n.setMessage(message); n.setSentAt(LocalDateTime.now()); notifications.save(n);
    }
}
