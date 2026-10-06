package com.ciao.backend.pattern.factory.schedule;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Driver;
import com.ciao.backend.entity.Route;
import com.ciao.backend.entity.Schedule;

import java.time.LocalDateTime;

public interface ScheduleTripFactory {
    Schedule create(Route route, Bus bus, Driver driver, LocalDateTime departure, LocalDateTime arrival);
}
