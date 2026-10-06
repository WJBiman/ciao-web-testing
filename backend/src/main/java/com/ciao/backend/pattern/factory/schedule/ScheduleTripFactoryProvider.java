package com.ciao.backend.pattern.factory.schedule;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Driver;
import com.ciao.backend.entity.Route;
import com.ciao.backend.entity.Schedule;

import java.time.LocalDateTime;

public final class ScheduleTripFactoryProvider {
    private ScheduleTripFactoryProvider() {}

    public static Schedule buildSchedule(boolean repeatDaily, Route route, Bus bus, Driver driver,
                                         LocalDateTime departure, LocalDateTime arrival) {
        ScheduleTripFactory factory = repeatDaily
                ? new DailyRecurringScheduleFactory()
                : new ExpressDirectScheduleFactory();
        return factory.create(route, bus, driver, departure, arrival);
    }
}
