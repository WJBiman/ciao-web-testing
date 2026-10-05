package com.ciao.backend.repository;

import com.ciao.backend.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {
    List<Schedule> findByStatus(Schedule.ScheduleStatus status);
    
    @Query("SELECT s FROM Schedule s WHERE (:origin = '' OR s.route.origin = :origin) " +
           "AND (:destination = '' OR s.route.destination = :destination) " +
           "AND s.status = com.ciao.backend.entity.Schedule$ScheduleStatus.SCHEDULED " +
           "AND s.route.status = com.ciao.backend.entity.Route$RouteStatus.ACTIVE AND s.departureTime > :now " +
           "AND s.departureTime >= :dateStart AND s.departureTime < :dateEnd " +
           "AND s.isCharter = false ORDER BY s.departureTime ASC, s.id ASC")
    List<Schedule> searchSchedules(@Param("origin") String origin, @Param("destination") String destination,
                                  @Param("now") java.time.LocalDateTime now,
                                  @Param("dateStart") java.time.LocalDateTime dateStart,
                                  @Param("dateEnd") java.time.LocalDateTime dateEnd);

    @Query("SELECT s.id FROM Schedule s WHERE (:origin = '' OR s.route.origin = :origin) " +
           "AND (:destination = '' OR s.route.destination = :destination) " +
           "AND s.route.status = com.ciao.backend.entity.Route$RouteStatus.ACTIVE " +
           "AND s.status <> com.ciao.backend.entity.Schedule$ScheduleStatus.CANCELLED " +
           "AND s.repeatDaily = true AND s.departureTime < :dateEnd " +
           "AND (s.repeatUntil IS NULL OR s.repeatUntil >= :travelDate) " +
           "AND s.isCharter = false ORDER BY s.id ASC")
    List<Integer> findRecurringTemplateIds(@Param("origin") String origin, @Param("destination") String destination,
                                          @Param("dateEnd") LocalDateTime dateEnd, @Param("travelDate") LocalDate travelDate);

    @Query("SELECT s FROM Schedule s WHERE s.route.origin = :origin AND s.route.destination = :destination " +
           "AND s.route.status = com.ciao.backend.entity.Route$RouteStatus.ACTIVE " +
           "AND s.status <> com.ciao.backend.entity.Schedule$ScheduleStatus.CANCELLED " +
           "AND s.repeatDaily = true AND s.departureTime < :dateEnd " +
           "AND (s.repeatUntil IS NULL OR s.repeatUntil >= :travelDate) " +
           "AND s.isCharter = false")
    List<Schedule> findRecurringTemplates(@Param("origin") String origin, @Param("destination") String destination,
                                           @Param("dateEnd") LocalDateTime dateEnd, @Param("travelDate") LocalDate travelDate);

    Optional<Schedule> findByRecurrenceParentIdAndDepartureTime(Integer parentId, LocalDateTime departureTime);
    List<Schedule> findByRecurrenceParentId(Integer parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Integer id);

    @Query("SELECT s FROM Schedule s WHERE s.bus.id = :busId " +
           "AND s.status IN (com.ciao.backend.entity.Schedule$ScheduleStatus.SCHEDULED, com.ciao.backend.entity.Schedule$ScheduleStatus.IN_TRANSIT) " +
           "AND s.departureTime < :end AND s.arrivalTime > :start")
    List<Schedule> findBusConflicts(@Param("busId") Integer busId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT s FROM Schedule s WHERE s.bus.id = :busId AND s.id <> :excludeId " +
           "AND s.status IN (com.ciao.backend.entity.Schedule$ScheduleStatus.SCHEDULED, com.ciao.backend.entity.Schedule$ScheduleStatus.IN_TRANSIT) " +
           "AND s.departureTime < :end AND s.arrivalTime > :start")
    List<Schedule> findBusConflictsExcluding(@Param("busId") Integer busId,
                                            @Param("excludeId") Integer excludeId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Query("SELECT s FROM Schedule s WHERE s.bus.id = :busId " +
           "AND s.status <> com.ciao.backend.entity.Schedule$ScheduleStatus.CANCELLED " +
           "AND s.repeatDaily = true")
    List<Schedule> findRecurringTemplatesForBus(@Param("busId") Integer busId);

    List<Schedule> findByBusId(Integer busId);

    @Query("SELECT s.bus.id FROM Schedule s WHERE s.id = :scheduleId")
    Optional<Integer> findBusIdByScheduleId(@Param("scheduleId") Integer scheduleId);

    @Query("SELECT s.isCharter FROM Schedule s WHERE s.id = :scheduleId")
    Optional<Boolean> findIsCharterByScheduleId(@Param("scheduleId") Integer scheduleId);
}

