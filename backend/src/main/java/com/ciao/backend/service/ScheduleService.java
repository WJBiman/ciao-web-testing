package com.ciao.backend.service;

import com.ciao.backend.dto.ScheduleRequest;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Driver;
import com.ciao.backend.entity.Route;
import com.ciao.backend.entity.Schedule;
import com.ciao.backend.repository.BusRepository;
import com.ciao.backend.repository.DriverRepository;
import com.ciao.backend.repository.RouteRepository;
import com.ciao.backend.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScheduleService {

    public static final int MAX_MATERIALIZE_RETRIES = 5;

    public static class AssociationChangedRetryException extends RuntimeException {
        public AssociationChangedRetryException(String message) {
            super(message);
        }
    }

    // Test hook for deterministic interleaving: invoked after candidate discovery but before lock acquisition
    private volatile Runnable onAfterCandidateDiscoveryHook = null;

    public void setOnAfterCandidateDiscoveryHook(Runnable hook) {
        this.onAfterCandidateDiscoveryHook = hook;
    }

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private EerService eer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    public List<Schedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }

    public List<Schedule> searchSchedules(String origin, String destination) {
        return searchSchedules(origin, destination, LocalDate.now());
    }

    public List<Schedule> searchSchedules(String origin, String destination, LocalDate travelDate) {
        if (travelDate.isBefore(LocalDate.now())) return List.of();
        String originFilter = origin == null ? "" : origin.trim();
        String destinationFilter = destination == null ? "" : destination.trim();
        materializeRecurringSchedules(originFilter, destinationFilter, travelDate);
        return scheduleRepository.searchSchedules(originFilter, destinationFilter, LocalDateTime.now(),
                travelDate.atStartOfDay(), travelDate.plusDays(1).atStartOfDay());
    }

    /**
     * TRANSACTION CALLING CONTRACT:
     * Recurring schedule materialization executes each attempt in an isolated, independent
     * transaction (PROPAGATION_REQUIRES_NEW). This ensures that if a concurrent bus assignment
     * change is detected, all locks acquired during that attempt are cleanly released on rollback
     * without poisoning or marking any outer transaction as rollback-only.
     *
     * Preconditions for callers with an active outer transaction:
     * 1. Fixture/Data Visibility: Any candidate templates, routes, or buses created or modified
     *    by the caller must be committed before invocation so the independent transaction can discover them.
     * 2. Lock Safety: Callers must not hold conflicting pessimistic write locks on the candidate
     *    schedules or buses in the outer transaction, otherwise the inner transaction will block
     *    waiting for the outer transaction's locks.
     */
    public void materializeRecurringSchedules(String origin, String destination, LocalDate travelDate) {
        if (travelDate.isBefore(LocalDate.now())) return;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int attempts = 0;
        while (attempts < MAX_MATERIALIZE_RETRIES) {
            attempts++;
            try {
                txTemplate.execute(status -> {
                    attemptMaterializeRecurringSchedules(origin, destination, travelDate);
                    return null;
                });
                return; // Successfully materialized
            } catch (AssociationChangedRetryException e) {
                if (attempts >= MAX_MATERIALIZE_RETRIES) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Concurrent conflict: Bus assignment changed repeatedly during recurring schedule materialization. Please retry.");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Materialization interrupted during retry.", ie);
                }
            }
        }
    }

    private void attemptMaterializeRecurringSchedules(String origin, String destination, LocalDate travelDate) {
        LocalDateTime dateEnd = travelDate.plusDays(1).atStartOfDay();

        // 1. Discover candidate template IDs without loading stale managed entities
        List<Integer> candidateIds = scheduleRepository.findRecurringTemplateIds(origin, destination, dateEnd, travelDate);
        if (candidateIds.isEmpty()) return;

        // 2. Discover associated bus IDs
        TreeSet<Integer> busIdsToLock = new TreeSet<>();
        for (Integer templateId : candidateIds) {
            scheduleRepository.findBusIdByScheduleId(templateId).ifPresent(busIdsToLock::add);
        }

        // Test synchronization hook: invoked after candidate discovery, before locking
        if (onAfterCandidateDiscoveryHook != null) {
            onAfterCandidateDiscoveryHook.run();
        }

        // 3. Acquire locks in strict canonical system order:
        //    All Buses ascending -> All Schedules ascending
        for (Integer busId : busIdsToLock) {
            busRepository.findByIdForUpdate(busId);
        }

        TreeSet<Integer> scheduleIdsToLock = new TreeSet<>(candidateIds);
        List<Schedule> lockedTemplates = new ArrayList<>();
        for (Integer templateId : scheduleIdsToLock) {
            scheduleRepository.findByIdForUpdate(templateId).ifPresent(lockedTemplates::add);
        }

        // 4. Revalidate associations under lock:
        //    Check if any candidate template's bus changed to a bus that was not in busIdsToLock.
        for (Schedule template : lockedTemplates) {
            Bus currentBus = template.getBus();
            if (currentBus != null && !busIdsToLock.contains(currentBus.getId())) {
                // Association changed! An earlier-order resource (Bus) is required.
                // Abort transaction and retry discovery in a new transaction. NEVER lock Bus while holding Schedule locks!
                throw new AssociationChangedRetryException("Bus assignment changed for template " + template.getId()
                        + " from " + busIdsToLock + " to bus " + currentBus.getId());
            }
        }

        // 5. Materialize each candidate under lock with full validation of current locked state
        for (Schedule template : lockedTemplates) {
            materializeFromLockedTemplate(template, travelDate);
        }
    }

    /**
     * TRANSACTION CALLING CONTRACT:
     * Executes each attempt in an isolated transaction (PROPAGATION_REQUIRES_NEW).
     * Any caller with an active outer transaction must commit candidate schedule/bus changes
     * before calling, and must not hold conflicting write locks on the target resources.
     */
    public void materializeSingleTemplateOccurrence(Integer templateId, LocalDate travelDate) {
        if (travelDate.isBefore(LocalDate.now())) return;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int attempts = 0;
        while (attempts < MAX_MATERIALIZE_RETRIES) {
            attempts++;
            try {
                txTemplate.execute(status -> {
                    attemptMaterializeSingleTemplateOccurrence(templateId, travelDate);
                    return null;
                });
                return; // Success
            } catch (AssociationChangedRetryException e) {
                if (attempts >= MAX_MATERIALIZE_RETRIES) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Concurrent conflict on schedule template " + templateId + ". Please retry.");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Materialization interrupted during retry.", ie);
                }
            }
        }
    }

    private void attemptMaterializeSingleTemplateOccurrence(Integer templateId, LocalDate travelDate) {
        // 1. Discover bus ID without loading managed schedule
        Optional<Integer> busIdOpt = scheduleRepository.findBusIdByScheduleId(templateId);

        // Test synchronization hook
        if (onAfterCandidateDiscoveryHook != null) {
            onAfterCandidateDiscoveryHook.run();
        }

        // 2. Lock Bus (if assigned)
        if (busIdOpt.isPresent() && busIdOpt.get() != null) {
            busRepository.findByIdForUpdate(busIdOpt.get());
        }

        // 3. Lock Schedule
        Schedule template = scheduleRepository.findByIdForUpdate(templateId).orElse(null);
        if (template == null) return;

        // 4. Revalidate association under lock
        Integer currentBusId = template.getBus() != null ? template.getBus().getId() : null;
        if (currentBusId != null && !Objects.equals(currentBusId, busIdOpt.orElse(null))) {
            // Association changed! Abort and retry in new transaction
            throw new AssociationChangedRetryException("Bus changed from " + busIdOpt + " to " + currentBusId);
        }

        // 5. Materialize
        materializeFromLockedTemplate(template, travelDate);
    }

    private void materializeFromLockedTemplate(Schedule template, LocalDate travelDate) {
        if (template == null || template.getStatus() == Schedule.ScheduleStatus.CANCELLED
                || template.isCharter() || !template.isRepeatDaily()) {
            return;
        }

        if (template.getRoute() == null || template.getRoute().getStatus() != Route.RouteStatus.ACTIVE) {
            return;
        }

        if (template.getDepartureTime() == null || template.getArrivalTime() == null) {
            return;
        }

        if (template.getRepeatUntil() != null && travelDate.isAfter(template.getRepeatUntil())) {
            return;
        }

        LocalDateTime departure = travelDate.atTime(template.getDepartureTime().toLocalTime());
        if (!departure.isAfter(LocalDateTime.now()) || departure.equals(template.getDepartureTime())) {
            return;
        }

        if (scheduleRepository.findByRecurrenceParentIdAndDepartureTime(template.getId(), departure).isPresent()) {
            return;
        }

        Bus bus = template.getBus();
        Duration duration = Duration.between(template.getDepartureTime(), template.getArrivalTime());
        LocalDateTime arrival = departure.plus(duration);

        if (bus != null) {
            // Re-read bus status under lock
            Bus currentBus = busRepository.findById(bus.getId()).orElse(null);
            if (currentBus == null || currentBus.getStatus() == Bus.BusStatus.RETIRED
                    || currentBus.getStatus() == Bus.BusStatus.MAINTENANCE) {
                return;
            }
            Optional<String> conflict = eer.checkBusConflictForPublicTrip(
                    currentBus.getId(),
                    null,
                    template.getId(),
                    departure,
                    arrival,
                    false,
                    null
            );
            if (conflict.isPresent()) {
                return; // Non-throwing expected conflict handling: skip materializing this occurrence
            }
            bus = currentBus;
        }

        Schedule occurrence = new Schedule();
        occurrence.setRoute(template.getRoute());
        occurrence.setBus(bus);
        occurrence.setDriver(template.getDriver());
        occurrence.setDepartureTime(departure);
        occurrence.setArrivalTime(arrival);
        occurrence.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        occurrence.setRepeatDaily(false);
        occurrence.setRepeatUntil(null);
        occurrence.setRecurrenceParentId(template.getId());
        scheduleRepository.save(occurrence);
    }

    @Transactional
    public Schedule createSchedule(ScheduleRequest request) {
        if (request.getArrivalTime() != null && request.getDepartureTime() != null) {
            if (!request.getArrivalTime().isAfter(request.getDepartureTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule arrival time must be after departure time.");
            }
        }
        if (request.getBusId() != null) {
            busRepository.findByIdForUpdate(request.getBusId());
        }
        // =========================================================================================
        // DESIGN PATTERN: FACTORY METHOD PATTERN (Creational)
        // ASSIGNED MEMBER: Warushawithana J.B. (IT25100691)
        // COMPONENT: Route & Schedule Management
        // EXPLANATION: Encapsulates creation of distinct transit schedule objects.
        //              ScheduleTripFactoryProvider resolves either DailyRecurringScheduleFactory
        //              or ExpressDirectScheduleFactory based on repeatDaily flag to configure
        //              proper recurrence chains and timetable slot intervals.
        // =========================================================================================
        boolean repeatDaily = Boolean.TRUE.equals(request.getRepeatDaily());
        Schedule schedule = com.ciao.backend.pattern.factory.schedule.ScheduleTripFactoryProvider.buildSchedule(
                repeatDaily, null, null, null, request.getDepartureTime(), request.getArrivalTime()
        );
        Schedule saved = updateScheduleFields(schedule, request);
        
        // Audit Log: Transit Schedule Creation Event (Assigned Member: Warushawithana J.B. - IT25100691)
        System.out.println("[FACTORY: SCHEDULE-TRIP] Created Transit Schedule Ref #" + saved.getId() 
                + " via " + (repeatDaily ? "DailyRecurringScheduleFactory" : "ExpressDirectScheduleFactory") 
                + " | Departure: " + saved.getDepartureTime() + " | RepeatDaily: " + saved.isRepeatDaily());
        
        return saved;
    }

    /**
     * TRANSACTION CALLING CONTRACT:
     * Executes schedule update attempts in isolated transactions (PROPAGATION_REQUIRES_NEW).
     * Callers with active outer transactions must commit modifications to target routes/buses
     * before calling, and must not hold conflicting write locks on the target schedule or buses.
     */
    public Schedule updateSchedule(Integer id, ScheduleRequest request) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int attempts = 0;
        while (attempts < MAX_MATERIALIZE_RETRIES) {
            attempts++;
            try {
                return txTemplate.execute(status -> attemptUpdateSchedule(id, request));
            } catch (AssociationChangedRetryException e) {
                if (attempts >= MAX_MATERIALIZE_RETRIES) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Concurrent conflict updating schedule. Bus assignment changed repeatedly. Please retry.");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Schedule update interrupted during retry.", ie);
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent conflict updating schedule.");
    }

    private Schedule attemptUpdateSchedule(Integer id, ScheduleRequest request) {
        TreeSet<Integer> busIdsToLock = new TreeSet<>();
        Integer oldBusId = scheduleRepository.findBusIdByScheduleId(id).orElse(null);
        if (oldBusId != null) busIdsToLock.add(oldBusId);
        if (request.getBusId() != null) busIdsToLock.add(request.getBusId());

        // Test synchronization hook: invoked after candidate discovery, before locking
        if (onAfterCandidateDiscoveryHook != null) {
            onAfterCandidateDiscoveryHook.run();
        }

        for (Integer bId : busIdsToLock) {
            busRepository.findByIdForUpdate(bId);
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + id));

        Integer actualBusId = (schedule.getBus() != null) ? schedule.getBus().getId() : null;
        if (actualBusId != null && !busIdsToLock.contains(actualBusId)) {
            throw new AssociationChangedRetryException("Schedule bus changed to " + actualBusId);
        }

        if (request.getArrivalTime() != null && request.getDepartureTime() != null) {
            if (!request.getArrivalTime().isAfter(request.getDepartureTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule arrival time must be after departure time.");
            }
        }
        if (schedule.isRepeatDaily() && !scheduleRepository.findByRecurrenceParentId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This daily service already has dated departures. Edit an individual departure, or cancel this series and create a new daily service.");
        }
        return updateScheduleFields(schedule, request);
    }

    private Schedule updateScheduleFields(Schedule schedule, ScheduleRequest request) {
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route not found with id: " + request.getRouteId()));
        if (route.getStatus() != Route.RouteStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign schedule to an INACTIVE route.");
        }
        schedule.setRoute(route);

        boolean repeatDaily = Boolean.TRUE.equals(request.getRepeatDaily());
        LocalDate repeatUntil = repeatDaily ? request.getRepeatUntil() : null;

        if (request.getBusId() != null) {
            Bus bus = eer.validateBusAvailableForPublicTrip(
                    request.getBusId(),
                    schedule.getId(),
                    schedule.getRecurrenceParentId(),
                    request.getDepartureTime(),
                    request.getArrivalTime(),
                    repeatDaily,
                    repeatUntil
            );
            schedule.setBus(bus);
        } else {
            schedule.setBus(null);
        }

        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver not found with id: " + request.getDriverId()));
            if (driver.getStatus() == Driver.DriverStatus.RETIRED || driver.getStatus() == Driver.DriverStatus.ON_LEAVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign a driver that is in " + driver.getStatus() + " status.");
            }
            schedule.setDriver(driver);
        } else {
            schedule.setDriver(null);
        }

        schedule.setDepartureTime(request.getDepartureTime());
        schedule.setArrivalTime(request.getArrivalTime());

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            schedule.setStatus(Schedule.ScheduleStatus.valueOf(request.getStatus().trim().toUpperCase()));
        } else if (schedule.getStatus() == null) {
            schedule.setStatus(Schedule.ScheduleStatus.SCHEDULED);
        }

        if (repeatDaily && (request.getBusId() == null || schedule.getRecurrenceParentId() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A daily service needs a bus and cannot be created from an individual repeated departure.");
        }
        if (repeatDaily && request.getRepeatUntil() != null
                && request.getRepeatUntil().isBefore(request.getDepartureTime().toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repeat-until date cannot be before the departure date.");
        }
        schedule.setRepeatDaily(repeatDaily);
        schedule.setRepeatUntil(repeatUntil);

        return scheduleRepository.save(schedule);
    }

    @Transactional
    public void cancelSchedule(Integer id) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + id));
        schedule.setStatus(Schedule.ScheduleStatus.CANCELLED);
        scheduleRepository.save(schedule);
        List<Schedule> occurrences = scheduleRepository.findByRecurrenceParentId(id).stream()
                .filter(item -> item.getDepartureTime().isAfter(LocalDateTime.now()) && item.getStatus() == Schedule.ScheduleStatus.SCHEDULED)
                .toList();
        if (!occurrences.isEmpty()) {
            occurrences.forEach(item -> item.setStatus(Schedule.ScheduleStatus.CANCELLED));
            scheduleRepository.saveAll(occurrences);
        }
    }
}
