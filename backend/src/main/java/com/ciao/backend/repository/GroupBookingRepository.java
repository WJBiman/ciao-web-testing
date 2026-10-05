package com.ciao.backend.repository;

import com.ciao.backend.entity.GroupBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GroupBookingRepository extends JpaRepository<GroupBooking, Integer> {
    Optional<GroupBooking> findByIdAndCustomerPhone(Integer id, String customerPhone);
    Optional<GroupBooking> findByGuestAccessToken(String guestAccessToken);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT g FROM GroupBooking g WHERE g.id = :id")
    Optional<GroupBooking> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT g FROM GroupBooking g WHERE g.assignedBus.id = :busId " +
           "AND g.id <> :excludeId " +
           "AND g.status IN (com.ciao.backend.entity.GroupBooking$GroupBookingStatus.APPROVED, com.ciao.backend.entity.GroupBooking$GroupBookingStatus.DEPOSIT_PAID) " +
           "AND g.startDate < :end AND g.endDate > :start")
    java.util.List<GroupBooking> findOverlappingGroupBookings(@org.springframework.data.repository.query.Param("busId") Integer busId,
                                                             @org.springframework.data.repository.query.Param("excludeId") Integer excludeId,
                                                             @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                                             @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE GroupBooking g SET g.status = :newStatus WHERE g.id = :id AND g.status IN :expectedStatuses")
    int atomicTransitionStatus(@org.springframework.data.repository.query.Param("id") Integer id,
                               @org.springframework.data.repository.query.Param("expectedStatuses") java.util.Collection<com.ciao.backend.entity.GroupBooking.GroupBookingStatus> expectedStatuses,
                               @org.springframework.data.repository.query.Param("newStatus") com.ciao.backend.entity.GroupBooking.GroupBookingStatus newStatus);

    @org.springframework.data.jpa.repository.Query("SELECT g.assignedBus.id FROM GroupBooking g WHERE g.id = :id")
    Optional<Integer> findAssignedBusIdByGroupId(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT g.booking.schedule.id FROM GroupBooking g WHERE g.id = :id")
    Optional<Integer> findScheduleIdByGroupId(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT g.status FROM GroupBooking g WHERE g.id = :id")
    Optional<com.ciao.backend.entity.GroupBooking.GroupBookingStatus> findStatusByGroupId(@org.springframework.data.repository.query.Param("id") Integer id);
}
