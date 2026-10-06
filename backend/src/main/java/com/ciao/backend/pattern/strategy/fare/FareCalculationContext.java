package com.ciao.backend.pattern.strategy.fare;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;

public final class FareCalculationContext {
    private FareCalculationContext() {}

    public static FareCalculationStrategy resolveStrategy(Bus bus, Route route) {
        String busType = bus == null || bus.getBusType() == null ? "" : bus.getBusType();
        String normalized = busType.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (normalized.contains("EXPRESSWAY") || normalized.contains("SUPER_LUXURY")
                || normalized.contains("SUPERLUXURY")) {
            return new ExpresswayLuxuryFareStrategy();
        }
        return new StandardNtcFareStrategy();
    }
}
