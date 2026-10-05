package com.ciao.backend.service;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.dto.reservation.PaymentResponse;
import com.ciao.backend.entity.Payment;
import com.ciao.backend.entity.Reservation;
import com.ciao.backend.entity.ReservedSeat;
import com.ciao.backend.repository.PaymentRepository;
import com.ciao.backend.repository.ReservationRepository;
import com.ciao.backend.repository.ReservedSeatRepository;
import com.ciao.backend.repository.ScheduleRepository;
import com.ciao.backend.entity.Schedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    @Autowired private EerService eer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservedSeatRepository reservedSeatRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Transactional
    public PaymentResponse processCheckout(PaymentRequest request, String username, Integer guestReservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(request.getReservationId())
                .orElseThrow(() -> new RuntimeException("Error: Reservation not found."));

        // Security Check: Ownership
        if (reservation.getUser() != null) {
            // Must belong to the authenticated user
            boolean owns = username != null && (username.equals(reservation.getUser().getEmail()) ||
                           username.equals(reservation.getUser().getPhone()));
            // In a real app, we might also allow ADMINs to process it, but usually admins don't pay for users directly
            if (!owns) {
                throw new RuntimeException("Error: Unauthorized. Reservation belongs to another user.");
            }
        } else {
            // Guest reservation
            if (guestReservationId == null || !guestReservationId.equals(reservation.getId())) {
                throw new RuntimeException("Error: Unauthorized. Invalid Guest Token for this reservation.");
            }
        }

        if (reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
            throw new RuntimeException("Error: Reservation is already " + reservation.getStatus());
        }

        // Serialize checkout with seat allocation for the same schedule.
        Schedule schedule = scheduleRepository.findByIdForUpdate(reservation.getSchedule().getId())
                .orElseThrow(() -> new RuntimeException("Error: Schedule not found."));
        LocalDateTime now = LocalDateTime.now();
        List<ReservedSeat> seats = reservedSeatRepository.findByReservationId(reservation.getId());
        boolean expired = seats.isEmpty() || seats.stream().anyMatch(seat ->
                seat.getStatus() != ReservedSeat.SeatStatus.LOCKED
                        || seat.getLockExpiresAt() == null || !seat.getLockExpiresAt().isAfter(now));
        if (expired || schedule.getStatus() != Schedule.ScheduleStatus.SCHEDULED || schedule.isCharter()
                || schedule.getDepartureTime() == null || !schedule.getDepartureTime().isAfter(now)) {
            reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            // Return a failure so the cancellation commits instead of rolling back.
            for (ReservedSeat seat : seats) {
                if (seat.getStatus() == ReservedSeat.SeatStatus.LOCKED) {
                    seat.setLockExpiresAt(now);
                    reservedSeatRepository.save(seat);
                }
            }
            eer.notifyBooking(reservation, "Your seat hold expired or the trip became unavailable.");
            return new PaymentResponse(null, "FAILED", schedule.isCharter() ? "This bus is reserved for a group booking. Your booking was cancelled and seats were released." : "Seat lock expired or schedule unavailable. Please restart booking.");
        }

        if (request.isCancel()) {
            reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            for (ReservedSeat seat : seats) {
                seat.setLockExpiresAt(now);
                reservedSeatRepository.save(seat);
            }
            eer.notifyBooking(reservation, "Booking cancelled. Your seats have been released.");
            return new PaymentResponse(null, "CANCELLED", "Demo payment cancelled. Seats released. No money was charged.");
        }
        boolean isSuccess = request.getCardNumber() != null
                && "4111111111111111".equals(request.getCardNumber().replace(" ", "").replace("-", ""))
                && request.getCardholderName() != null
                && "CIAO TEST".equalsIgnoreCase(request.getCardholderName().trim())
                && "12/30".equals(request.getExpiry())
                && "123".equals(request.getCvv());
        String txId = "DEMO-" + UUID.randomUUID().toString();
        Payment.PaymentMethod method = Payment.PaymentMethod.CARD;

        Payment payment = new Payment(
                reservation,
                reservation.getTotalFare(),
                method,
                isSuccess ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED,
                txId
        );
        paymentRepository.save(payment);

        if (isSuccess) {
            // Atomically update Reservation and Seats
            reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);

            for (ReservedSeat seat : seats) {
                seat.setStatus(ReservedSeat.SeatStatus.BOOKED);
                seat.setLockExpiresAt(null);
                reservedSeatRepository.save(seat);
            }
            eer.recordPayment(payment);
            eer.notifyBooking(reservation, "Payment accepted. Your booking is confirmed and your ticket is ready.");
            return new PaymentResponse(txId, "SUCCESS", "Demo payment successful. Seats confirmed. No money was charged.");
        } else {
            eer.recordPayment(payment);
            // Payment failed. Reservation remains PENDING. Seats remain LOCKED until expiry.
            return new PaymentResponse(txId, "FAILED", "Card declined. Only the supplied Ciao test card details are accepted. Check all four fields and retry before the seat hold expires.");
        }
    }
}

