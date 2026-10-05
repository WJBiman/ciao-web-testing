package com.ciao.backend.service;

import com.ciao.backend.dto.reservation.ReservationRequest;
import com.ciao.backend.dto.reservation.ReservationResponse;
import com.ciao.backend.entity.Reservation;
import com.ciao.backend.entity.ReservedSeat;
import com.ciao.backend.entity.Schedule;
import com.ciao.backend.entity.User;
import com.ciao.backend.repository.ReservationRepository;
import com.ciao.backend.repository.ReservedSeatRepository;
import com.ciao.backend.repository.ScheduleRepository;
import com.ciao.backend.repository.UserRepository;
import com.ciao.backend.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationService {
    @Autowired private EerService eer;
    @Autowired private com.ciao.backend.repository.TicketRepository tickets;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservedSeatRepository reservedSeatRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private com.ciao.backend.repository.BusRepository busRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    public List<String> getUnavailableSeats(Integer scheduleId) {
        return reservedSeatRepository.findAllUnavailableSeatsForSchedule(scheduleId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getSeatMap(Integer scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        int capacity = schedule.getBus() != null && schedule.getBus().getCapacity() != null
                ? schedule.getBus().getCapacity() : 49;
        boolean bookable = !schedule.isCharter()
                && schedule.getRoute().getStatus() == com.ciao.backend.entity.Route.RouteStatus.ACTIVE
                && schedule.getStatus() == Schedule.ScheduleStatus.SCHEDULED
                && schedule.getDepartureTime() != null && schedule.getDepartureTime().isAfter(LocalDateTime.now());
        BigDecimal effectiveSeatFare = NTCFareCalculator.calculateSeatFare(schedule);
        String busType = schedule.getBus() != null ? schedule.getBus().getBusType() : null;
        return java.util.Map.of("capacity", capacity, "unavailableSeats", getUnavailableSeats(scheduleId),
                "baseFare", schedule.getRoute().getBaseFare(), "effectiveSeatFare", effectiveSeatFare,
                "busType", busType != null ? busType : "Standard",
                "bookable", bookable, "isCharter", schedule.isCharter());
    }

    @Transactional
    public ReservationResponse lockSeats(ReservationRequest request, String username) {
        // Consistent lock order across system: Bus -> Schedule -> Reservation
        Optional<Integer> busIdOpt = scheduleRepository.findBusIdByScheduleId(request.getScheduleId());
        com.ciao.backend.entity.Bus bus = null;
        if (busIdOpt.isPresent() && busIdOpt.get() != null) {
            bus = busRepository.findByIdForUpdate(busIdOpt.get()).orElse(null);
        }

        // 1. Acquire Pessimistic Write Lock on the Schedule
        Schedule schedule = scheduleRepository.findByIdForUpdate(request.getScheduleId())
                .orElseThrow(() -> new RuntimeException("Error: Schedule not found."));

        if (schedule.isCharter()) {
            throw new RuntimeException("This bus schedule is reserved for a group booking and is unavailable for individual bookings.");
        }

        if (schedule.getRoute().getStatus() != com.ciao.backend.entity.Route.RouteStatus.ACTIVE
                || schedule.getStatus() != Schedule.ScheduleStatus.SCHEDULED
                || schedule.getDepartureTime() == null
                || !schedule.getDepartureTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Error: This schedule is no longer available for booking.");
        }

        // Validate seats list
        List<String> rawSeats = request.getSeatNumbers();
        if (rawSeats == null || rawSeats.isEmpty()) {
            throw new RuntimeException("Error: At least one seat must be selected.");
        }

        // Check for duplicates in the request
        java.util.Set<String> uniqueSeats = new java.util.HashSet<>();
        int maxCapacity = (bus != null && bus.getCapacity() != null)
                ? bus.getCapacity()
                : (schedule.getBus() != null && schedule.getBus().getCapacity() != null ? schedule.getBus().getCapacity() : 49);

        for (String seat : rawSeats) {
            if (seat == null || seat.trim().isEmpty()) {
                throw new RuntimeException("Error: Invalid blank seat identifier.");
            }
            String trimmedSeat = seat.trim();
            if (!seat.equals(trimmedSeat) || !trimmedSeat.matches("[1-9][0-9]*")) {
                throw new RuntimeException("Error: Seat numbers must use standard numbering (for example, 1, not 01).");
            }
            if (!uniqueSeats.add(trimmedSeat)) {
                throw new RuntimeException("Error: Duplicate seat number '" + trimmedSeat + "' in request.");
            }
            try {
                int seatNum = Integer.parseInt(trimmedSeat);
                if (seatNum < 1 || seatNum > maxCapacity) {
                    throw new RuntimeException("Error: Seat number '" + trimmedSeat + "' is out of valid range (1 - " + maxCapacity + ").");
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("Error: Malformed seat number '" + trimmedSeat + "'. Must be numeric.");
            }
        }
        
        // 2. Check if requested seats are already locked or booked using unified application time reference
        LocalDateTime now = LocalDateTime.now();
        List<String> unavailable = reservedSeatRepository.findUnavailableSeats(schedule.getId(), request.getSeatNumbers(), now);
        if (!unavailable.isEmpty()) {
            throw new RuntimeException("Error: The following seats are already taken: " + String.join(", ", unavailable));
        }

        // 3. Resolve User (if authenticated)
        User user = null;
        if (username != null) {
            user = userRepository.findByEmailIgnoreCase(username)
                    .orElse(userRepository.findByPhone(username)
                            .orElse(null));
        }

        String passName = request.getPassengerName() != null ? request.getPassengerName().trim() : "";
        String passPhone = request.getPassengerPhone() != null ? request.getPassengerPhone().trim() : "";
        if (passName.isEmpty()) passName = user != null ? user.getFullName() : "Guest User";
        if (passPhone.isEmpty()) passPhone = user != null ? user.getPhone() : "0770000000";

        // 4. Calculate total fare using authoritative NTC tier multiplier and route fare
        BigDecimal totalFare = NTCFareCalculator.calculateTotalFare(schedule, request.getSeatNumbers().size());

        // 5. Create Reservation (PENDING)
        Reservation reservation = new Reservation(
                schedule,
                user,
                passName,
                passPhone,
                totalFare,
                Reservation.ReservationStatus.PENDING
        );
        reservation = reservationRepository.save(reservation);

        // 6. Create ReservedSeats (LOCKED for 10 minutes using same time reference)
        LocalDateTime lockExpiry = now.plusMinutes(10);
        for (String seatNum : request.getSeatNumbers()) {
            ReservedSeat rs = new ReservedSeat(
                    reservation,
                    seatNum,
                    lockExpiry,
                    ReservedSeat.SeatStatus.LOCKED
            );
            reservedSeatRepository.save(rs);
        }

        eer.syncReservation(reservation);
        // 7. Generate Guest Token if user is null
        String guestToken = null;
        if (user == null) {
            guestToken = jwtUtils.generateGuestReservationToken(reservation.getId());
        }

        return new ReservationResponse(reservation, request.getSeatNumbers(), guestToken);
    }

    public Reservation getRawReservation(Integer reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
    }

    public ReservationResponse getTicketDetails(Integer reservationId) {
        Reservation reservation = getRawReservation(reservationId);
        
        List<String> seats = reservedSeatRepository.findByReservationId(reservationId).stream()
                .map(ReservedSeat::getSeatNumber)
                .toList();

        ReservationResponse response=new ReservationResponse(reservation, seats, null);
        tickets.findByReservationId(reservationId).ifPresent(ticket -> {response.setTicketId(ticket.getId());response.setQrCode(ticket.getQrCode());response.setIssueDate(ticket.getIssueDate());});
        return response;
    }

    public List<com.ciao.backend.dto.reservation.UserReservationDTO> getUserReservations(String username) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userRepository.findByEmailIgnoreCase(username)
                .orElseGet(() -> userRepository.findByPhone(username)
                        .orElseThrow(() -> new RuntimeException("User not found")));

        List<Reservation> userReservations = reservationRepository.findByUserIdOrderByIdDesc(user.getId());

        return userReservations.stream().map(res -> {
            List<String> seats = reservedSeatRepository.findByReservationId(res.getId()).stream()
                    .map(ReservedSeat::getSeatNumber)
                    .toList();
            return new com.ciao.backend.dto.reservation.UserReservationDTO(res, seats);
        }).toList();
    }
}

