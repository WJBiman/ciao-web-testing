package com.ciao.backend;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RecurringScheduleTest {
    @Autowired ScheduleService service;
    @Autowired ScheduleRepository schedules;
    @Autowired RouteRepository routes;

    private Schedule template() {
        Route route = routes.save(new Route("Daily-" + UUID.randomUUID(), "Destination", new BigDecimal("500"), Route.RouteStatus.ACTIVE));
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(23, 0);
        Schedule schedule = new Schedule(route, null, null, departure, departure.plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        schedule.setRepeatDaily(true);
        return schedules.save(schedule);
    }
    private List<Schedule> search(Schedule template, int days) {
        return service.searchSchedules(template.getRoute().getOrigin(), "Destination", LocalDate.now().plusDays(days));
    }

    @Test void anyCityFiltersIncludeAndMaterializeFutureDepartures() {
        Schedule template = template();
        LocalDate travelDate = LocalDate.now().plusDays(2);

        List<Schedule> byOrigin = service.searchSchedules(template.getRoute().getOrigin(), "", travelDate);
        Schedule occurrence = byOrigin.stream()
                .filter(s -> template.getId().equals(s.getRecurrenceParentId()))
                .findFirst().orElseThrow();

        assertTrue(service.searchSchedules("", "Destination", travelDate).stream()
                .anyMatch(s -> s.getId().equals(occurrence.getId())));
        assertTrue(schedules.findRecurringTemplateIds("", "", travelDate.plusDays(1).atStartOfDay(), travelDate)
                .contains(template.getId()));
        assertTrue(schedules.searchSchedules("", "", LocalDateTime.now(), travelDate.atStartOfDay(),
                travelDate.plusDays(1).atStartOfDay()).stream()
                .anyMatch(s -> s.getId().equals(occurrence.getId())));
    }

    @Test void dailyDeparturesKeepSeparateIdsAndOvernightArrivalAndRespectEndDate() {
        Schedule template = template();
        template.setRepeatUntil(LocalDate.now().plusDays(3));
        schedules.save(template);
        Schedule second = search(template, 2).get(0);
        Schedule third = search(template, 3).get(0);
        assertNotEquals(second.getId(), third.getId());
        assertEquals(second.getId(), search(template, 2).get(0).getId());
        assertEquals(LocalDate.now().plusDays(3).atTime(2, 0), second.getArrivalTime());
        assertTrue(search(template, 4).isEmpty());
        assertTrue(search(template, 0).isEmpty());
    }

    @Test void cancellingOneDepartureDoesNotRecreateItOrCancelOtherDays() {
        Schedule template = template();
        service.cancelSchedule(search(template, 2).get(0).getId());
        assertTrue(search(template, 2).isEmpty());
        assertEquals(1, search(template, 3).size());
        service.cancelSchedule(template.getId());
        assertTrue(search(template, 3).isEmpty());
        assertTrue(search(template, 4).isEmpty());
    }

    @Test void completedFirstDepartureStillRepeatsAndInactiveRouteStopsSearch() {
        Schedule template = template();
        template.setStatus(Schedule.ScheduleStatus.COMPLETED);
        schedules.save(template);
        assertEquals(1, search(template, 2).size());
        Route route = template.getRoute();
        route.setStatus(Route.RouteStatus.INACTIVE);
        routes.save(route);
        assertTrue(search(template, 2).isEmpty());
        assertTrue(search(template, 3).isEmpty());
    }

    @Autowired BusRepository buses;
    @Autowired com.ciao.backend.service.EerService eer;

    @Test void concurrentSearchesCreateOnlyOneDeparture() throws Exception {
        Schedule template = template();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) jobs.add(() -> search(template, 2).get(0).getId());
            Set<Integer> ids = new HashSet<>();
            for (Future<Integer> result : pool.invokeAll(jobs)) ids.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(1, schedules.findByRecurrenceParentId(template.getId()).size());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void conflictsBeyondDay365AndIndefiniteRecurringServicesWithoutArbitraryCutoffs() {
        Bus bus = new Bus();
        bus.setPlateNumber("REC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setCapacity(45);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        Route route = routes.save(new Route("Origin-Day400-" + UUID.randomUUID(), "Dest-Day400", new BigDecimal("600"), Route.RouteStatus.ACTIVE));
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(9, 0);
        Schedule template = new Schedule(route, bus, null, departure, departure.plusHours(4), Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(null); // Indefinite recurring service
        template = schedules.save(template);

        // Verify future search on Day 400 (> Day 365) materializes and returns occurrence
        LocalDate day400 = LocalDate.now().plusDays(400);
        List<Schedule> resultsDay400 = service.searchSchedules(route.getOrigin(), "Dest-Day400", day400);
        assertEquals(1, resultsDay400.size());
        assertEquals(bus.getId(), resultsDay400.get(0).getBus().getId());
        assertEquals(day400.atTime(9, 0), resultsDay400.get(0).getDepartureTime());
        assertEquals(day400.atTime(13, 0), resultsDay400.get(0).getArrivalTime());

        // Verify conflict detection beyond day 365 on that bus
        LocalDateTime conflictStart = day400.atTime(10, 0);
        LocalDateTime conflictEnd = day400.atTime(12, 0);

        Optional<String> publicConflict = eer.checkBusConflictForPublicTrip(
                bus.getId(), null, null, conflictStart, conflictEnd, false, null
        );
        assertTrue(publicConflict.isPresent(), "Expected public trip conflict on Day 400 against indefinite recurring template");

        final Bus fBus = bus;
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                eer.validateBusAssignmentForCharter(fBus.getId(), null, conflictStart, conflictEnd, 20),
                "Charter assignment on Day 400 must be rejected due to conflicting daily recurring service"
        );
    }

    @Test
    void overnightServicesNonIntersectingDepartureDatesOverlappingJourneyIntervals() {
        Bus bus = new Bus();
        bus.setPlateNumber("OVR-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setCapacity(45);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        // Daily Service A: Departs Day 10 at 22:00, arrives Day 11 at 04:00 (active only on Day 10)
        LocalDate day10 = LocalDate.now().plusDays(10);
        Route routeA = routes.save(new Route("OriginA-" + UUID.randomUUID(), "DestA", new BigDecimal("700"), Route.RouteStatus.ACTIVE));
        LocalDateTime depA = day10.atTime(22, 0);
        Schedule tmplA = new Schedule(routeA, bus, null, depA, depA.plusHours(6), Schedule.ScheduleStatus.SCHEDULED);
        tmplA.setRepeatDaily(true);
        tmplA.setRepeatUntil(day10); // Ends on Day 10
        tmplA = schedules.save(tmplA);

        // Daily Service B: Starts on Day 11, departs at 02:00, arrives at 08:00
        // Departure date ranges DO NOT intersect: [Day 10, Day 10] vs [Day 11, Day 15]
        LocalDate day11 = day10.plusDays(1);
        LocalDateTime depB = day11.atTime(2, 0);
        LocalDateTime arrB = depB.plusHours(6);

        // Candidate recurring service B on the same bus must detect overlap with Service A's overnight arrival
        Optional<String> conflictB = eer.checkBusConflictForPublicTrip(
                bus.getId(), null, null, depB, arrB, true, day11.plusDays(4)
        );
        assertTrue(conflictB.isPresent(), "Expected conflict detection for overnight service crossing midnight boundary into non-intersecting start date");
    }

    @Test
    void serviceStartAndRepeatUntilBoundaries() {
        Route route = routes.save(new Route("Boundary-" + UUID.randomUUID(), "Destination", new BigDecimal("500"), Route.RouteStatus.ACTIVE));
        LocalDate startDay = LocalDate.now().plusDays(1);
        LocalDate endDay = LocalDate.now().plusDays(5);
        LocalDateTime departure = startDay.atTime(10, 0);
        Schedule template = new Schedule(route, null, null, departure, departure.plusHours(2), Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(endDay);
        template = schedules.save(template);

        // Day 0 (before start) -> empty
        assertTrue(service.searchSchedules(route.getOrigin(), "Destination", LocalDate.now()).isEmpty());

        // Day 1 (start day) -> contains template departure
        assertEquals(1, service.searchSchedules(route.getOrigin(), "Destination", startDay).size());

        // Day 5 (repeatUntil boundary) -> materialized and present
        assertEquals(1, service.searchSchedules(route.getOrigin(), "Destination", endDay).size());

        // Day 6 (one day past repeatUntil boundary) -> empty
        assertTrue(service.searchSchedules(route.getOrigin(), "Destination", endDay.plusDays(1)).isEmpty());
    }

    @Test
    void busBackedFutureSearchesAndOwnTemplateExclusion() {
        Bus bus = new Bus();
        bus.setPlateNumber("BBS-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bus.setCapacity(40);
        bus.setStatus(Bus.BusStatus.ACTIVE);
        bus = buses.save(bus);

        Route route = routes.save(new Route("BusBacked-" + UUID.randomUUID(), "Destination", new BigDecimal("450"), Route.RouteStatus.ACTIVE));
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(14, 0);
        Schedule template = new Schedule(route, bus, null, departure, departure.plusHours(3), Schedule.ScheduleStatus.SCHEDULED);
        template.setRepeatDaily(true);
        template.setRepeatUntil(LocalDate.now().plusDays(30));
        template = schedules.save(template);

        // Search day 15
        LocalDate day15 = LocalDate.now().plusDays(15);
        List<Schedule> res1 = service.searchSchedules(route.getOrigin(), "Destination", day15);
        assertEquals(1, res1.size());
        Schedule occ = res1.get(0);
        assertEquals(template.getId(), occ.getRecurrenceParentId());
        assertEquals(bus.getId(), occ.getBus().getId());

        // Subsequent search returns the same materialized departure without duplicate creation
        List<Schedule> res2 = service.searchSchedules(route.getOrigin(), "Destination", day15);
        assertEquals(1, res2.size());
        assertEquals(occ.getId(), res2.get(0).getId());

        // Own-template exclusion in conflict checking
        Optional<String> selfConflict = eer.checkBusConflictForPublicTrip(
                bus.getId(), null, template.getId(), occ.getDepartureTime(), occ.getArrivalTime(), false, null
        );
        assertTrue(selfConflict.isEmpty(), "Own template must be excluded from conflict detection");
    }
}
