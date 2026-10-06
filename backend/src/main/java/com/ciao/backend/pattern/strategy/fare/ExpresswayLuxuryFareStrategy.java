package com.ciao.backend.pattern.strategy.fare;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;
import com.ciao.backend.service.NTCFareCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExpresswayLuxuryFareStrategy implements FareCalculationStrategy {
    @Override
    public BigDecimal calculateFare(Route route, Bus bus) {
        if (route == null || route.getBaseFare() == null) return BigDecimal.ZERO;
        return route.getBaseFare()
                .multiply(NTCFareCalculator.MULTIPLIER_SUPER_LUXURY)
                .add(NTCFareCalculator.EXPRESSWAY_TOLL)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "ExpresswayLuxuryFareStrategy";
    }
}
