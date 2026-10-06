package com.ciao.backend.pattern.strategy.fare;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;
import com.ciao.backend.service.NTCFareCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StandardNtcFareStrategy implements FareCalculationStrategy {
    @Override
    public BigDecimal calculateFare(Route route, Bus bus) {
        if (route == null || route.getBaseFare() == null) return BigDecimal.ZERO;
        String busType = bus == null ? null : bus.getBusType();
        return route.getBaseFare()
                .multiply(NTCFareCalculator.getTierMultiplier(busType))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "StandardNtcFareStrategy";
    }
}
