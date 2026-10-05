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

    public static BigDecimal calculateSeatFare(Route route, Bus bus) {
        if (route == null || route.getBaseFare() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseFare = route.getBaseFare();
        String busType = (bus != null) ? bus.getBusType() : null;
        BigDecimal multiplier = getTierMultiplier(busType);

        BigDecimal seatFare = baseFare.multiply(multiplier);

        // Add expressway toll surcharge for Super Luxury / Expressway service
        if (busType != null) {
            String normalized = busType.trim().toUpperCase().replace("-", "_").replace(" ", "_");
            if (normalized.contains("EXPRESSWAY") || normalized.contains("SUPER_LUXURY") || normalized.contains("SUPERLUXURY")) {
                seatFare = seatFare.add(EXPRESSWAY_TOLL);
            }
        }

        return seatFare.setScale(2, RoundingMode.HALF_UP);
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
