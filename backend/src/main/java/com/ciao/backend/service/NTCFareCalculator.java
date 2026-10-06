package com.ciao.backend.service;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;
import com.ciao.backend.entity.Schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * University Demo Tariff Engine (Ciao Bus Service).
 *
 * Computes authoritative seat fare and total booking fare across the application:
 * - Route base fare (proportional to journey distance)
 * - University demo tariff tier multipliers (simulated rate model for academic demonstration;
 *   not to be construed as official Gazette / NTC published rates without statutory tariff documentation):
 *     - NORMAL / STANDARD / null: 1.00x
 *     - SEMI_LUXURY: 1.30x
 *     - LUXURY / LUXURY_AC: 1.50x
 *     - SUPER_LUXURY / EXPRESSWAY: 2.00x + LKR 400.00 expressway demo toll
 */
public final class NTCFareCalculator {

    private NTCFareCalculator() {}

    public static final BigDecimal MULTIPLIER_NORMAL = new BigDecimal("1.00");
    public static final BigDecimal MULTIPLIER_SEMI_LUXURY = new BigDecimal("1.30");
    public static final BigDecimal MULTIPLIER_LUXURY = new BigDecimal("1.50");
    public static final BigDecimal MULTIPLIER_SUPER_LUXURY = new BigDecimal("2.00");
    public static final BigDecimal EXPRESSWAY_TOLL = new BigDecimal("400.00");

    public static BigDecimal getTierMultiplier(String busType) {
        if (busType == null || busType.trim().isEmpty()) {
            return MULTIPLIER_NORMAL;
        }
        String normalized = busType.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.contains("SUPER_LUXURY") || normalized.contains("SUPERLUXURY") || normalized.contains("EXPRESSWAY")) {
            return MULTIPLIER_SUPER_LUXURY;
        } else if (normalized.contains("LUXURY") || normalized.contains("AC")) {
            if (normalized.contains("SEMI")) {
                return MULTIPLIER_SEMI_LUXURY;
            }
            return MULTIPLIER_LUXURY;
        } else if (normalized.contains("SEMI")) {
            return MULTIPLIER_SEMI_LUXURY;
        }
        return MULTIPLIER_NORMAL;
    }

    // =========================================================================================
    // DESIGN PATTERN: STRATEGY PATTERN (Behavioral)
    // ASSIGNED MEMBER: Jahaas M.J.M. (IT25102586)
    // COMPONENT: Seat Allocation & Dynamic Fare Calculation
    // EXPLANATION: Dynamically delegates fare calculation to either StandardNtcFareStrategy
    //              or ExpresswayLuxuryFareStrategy based on bus tier and route expressway flag.
    // =========================================================================================
    public static BigDecimal calculateSeatFare(Route route, Bus bus) {
        if (route == null || route.getBaseFare() == null) {
            return BigDecimal.ZERO;
        }
        com.ciao.backend.pattern.strategy.fare.FareCalculationStrategy strategy =
                com.ciao.backend.pattern.strategy.fare.FareCalculationContext.resolveStrategy(bus, route);
        BigDecimal fare = strategy.calculateFare(route, bus);
        
        // Operational Audit: Dynamic Fare Resolution Event (Assigned Member: Jahaas M.J.M. - IT25102586)
        System.out.println("[STRATEGY: FARE-CALCULATION] Executed " + strategy.getStrategyName() 
                + " for Bus [" + (bus != null ? bus.getPlateNumber() + " (" + bus.getBusType() + ")" : "N/A") 
                + "] on Route [" + (route != null ? route.getOrigin() + " -> " + route.getDestination() : "N/A") 
                + "] -> Calculated Seat Fare: LKR " + fare);
        
        return fare;
    }

    public static BigDecimal calculateSeatFare(Schedule schedule) {
        if (schedule == null || schedule.getRoute() == null) {
            return BigDecimal.ZERO;
        }
        return calculateSeatFare(schedule.getRoute(), schedule.getBus());
    }

    public static BigDecimal calculateTotalFare(Schedule schedule, int seatCount) {
        if (seatCount <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal singleSeatFare = calculateSeatFare(schedule);
        return singleSeatFare.multiply(new BigDecimal(seatCount)).setScale(2, RoundingMode.HALF_UP);
    }
}
