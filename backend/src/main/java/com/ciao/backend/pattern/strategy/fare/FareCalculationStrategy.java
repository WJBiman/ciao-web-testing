package com.ciao.backend.pattern.strategy.fare;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;

import java.math.BigDecimal;

public interface FareCalculationStrategy {
    BigDecimal calculateFare(Route route, Bus bus);
    String getStrategyName();
}
