package com.ciao.backend.repository;

import com.ciao.backend.entity.ReservedSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Integer> {
    
    // Check which of the requested seats for a schedule are currently unavailable (Booked OR Locked & unexpired)
    @Query("SELECT rs.seatNumber FROM ReservedSeat rs " +
           "WHERE rs.reservation.schedule.id = :scheduleId " +
           "AND rs.seatNumber IN :seatNumbers " +
           "AND (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.BOOKED OR " +
           "    (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.LOCKED AND rs.lockExpiresAt > :now))")
    List<String> findUnavailableSeats(@Param("scheduleId") Integer scheduleId, 
                                      @Param("seatNumbers") List<String> seatNumbers,
                                      @Param("now") java.time.LocalDateTime now);

    // Get all unavailable seats for a schedule
    @Query("SELECT rs.seatNumber FROM ReservedSeat rs " +
           "WHERE rs.reservation.schedule.id = :scheduleId " +
           "AND (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.BOOKED OR " +
           "    (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.LOCKED AND rs.lockExpiresAt > :now))")
    List<String> findAllUnavailableSeatsForSchedule(@Param("scheduleId") Integer scheduleId,
                                                    @Param("now") java.time.LocalDateTime now);
    
    List<ReservedSeat> findByReservationId(Integer reservationId);

    @Query("SELECT rs FROM ReservedSeat rs " +
           "WHERE rs.reservation.schedule.bus.id = :busId " +
           "AND (rs.reservation.schedule.departureTime > :now OR rs.reservation.schedule.status = com.ciao.backend.entity.Schedule$ScheduleStatus.IN_TRANSIT) " +
           "AND (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.BOOKED OR " +
           "    (rs.status = com.ciao.backend.entity.ReservedSeat$SeatStatus.LOCKED AND rs.lockExpiresAt > :now))")
    List<ReservedSeat> findActiveAllocationsForBus(@Param("busId") Integer busId, @Param("now") java.time.LocalDateTime now);
}

