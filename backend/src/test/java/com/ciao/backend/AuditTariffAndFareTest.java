package com.ciao.backend;

import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Route;
import com.ciao.backend.entity.Schedule;
import com.ciao.backend.service.NTCFareCalculator;
import com.ciao.backend.service.ParcelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuditTariffAndFareTest {

    @Test
    @DisplayName("NTC Fare: Standard bus multiplier is 1.0x with base fare preserved")
    void testStandardBusMultiplier() {
        Route route = new Route("Colombo", "Kandy", new BigDecimal("1450.00"), Route.RouteStatus.ACTIVE);
        Bus standardBus = new Bus("NC-1234", 49, "WiFi", Bus.BusStatus.ACTIVE);
        standardBus.setBusType("NORMAL");

        Schedule schedule = new Schedule(route, standardBus, null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3), Schedule.ScheduleStatus.SCHEDULED);

        BigDecimal seatFare = NTCFareCalculator.calculateSeatFare(schedule);
        assertEquals(new BigDecimal("1450.00"), seatFare);

        BigDecimal totalFare = NTCFareCalculator.calculateTotalFare(schedule, 3);
        assertEquals(new BigDecimal("4350.00"), totalFare);
    }

    @Test
    @DisplayName("NTC Fare: Semi-Luxury bus multiplier is 1.30x")
    void testSemiLuxuryMultiplier() {
        Route route = new Route("Colombo", "Galle", new BigDecimal("1000.00"), Route.RouteStatus.ACTIVE);
        Bus semiBus = new Bus("NC-5678", 40, "WiFi, Reclining", Bus.BusStatus.ACTIVE);
        semiBus.setBusType("SEMI_LUXURY");

        Schedule schedule = new Schedule(route, semiBus, null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), Schedule.ScheduleStatus.SCHEDULED);

        BigDecimal seatFare = NTCFareCalculator.calculateSeatFare(schedule);
        assertEquals(new BigDecimal("1300.00"), seatFare);

        BigDecimal totalFare = NTCFareCalculator.calculateTotalFare(schedule, 2);
        assertEquals(new BigDecimal("2600.00"), totalFare);
    }

    @Test
    @DisplayName("NTC Fare: Luxury AC bus multiplier is 1.50x")
    void testLuxuryMultiplier() {
        Route route = new Route("Colombo", "Jaffna", new BigDecimal("2000.00"), Route.RouteStatus.ACTIVE);
        Bus luxuryBus = new Bus("ND-9999", 40, "AC, WiFi, Movies", Bus.BusStatus.ACTIVE);
        luxuryBus.setBusType("LUXURY_AC");

        Schedule schedule = new Schedule(route, luxuryBus, null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(7), Schedule.ScheduleStatus.SCHEDULED);

        BigDecimal seatFare = NTCFareCalculator.calculateSeatFare(schedule);
        assertEquals(new BigDecimal("3000.00"), seatFare);
    }

    @Test
    @DisplayName("University Demo Tariff: Super Luxury Expressway adds toll surcharge")
    void testSuperLuxuryExpresswayWithToll() {
        Route route = new Route("Colombo", "Matara", new BigDecimal("1200.00"), Route.RouteStatus.ACTIVE);
        Bus superBus = new Bus("ND-8888", 45, "Luxury Reclining, AC", Bus.BusStatus.ACTIVE);
        superBus.setBusType("SUPER_LUXURY_EXPRESSWAY");

        Schedule schedule = new Schedule(route, superBus, null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), Schedule.ScheduleStatus.SCHEDULED);

        // 1200 * 2.00 = 2400 + 400 (expressway demo toll) = 2800.00
        BigDecimal seatFare = NTCFareCalculator.calculateSeatFare(schedule);
        assertEquals(new BigDecimal("2800.00"), seatFare);

        // Schedule entity transient getter exposes identical effectiveSeatFare
        assertEquals(new BigDecimal("2800.00"), schedule.getEffectiveSeatFare());

        BigDecimal totalFare = NTCFareCalculator.calculateTotalFare(schedule, 2);
        assertEquals(new BigDecimal("5600.00"), totalFare);
    }

    @Test
    @DisplayName("Fare Consistency: All tiers preserve consistent effective fare from search to reservation hold")
    void testAllTiersFareConsistency() {
        String[][] tierMatrix = {
            {"NORMAL", "1000.00", "1000.00"},
            {"SEMI_LUXURY", "1000.00", "1300.00"},
            {"LUXURY_AC", "1000.00", "1500.00"},
            {"SUPER_LUXURY", "1000.00", "2400.00"} // 1000 * 2.00 + 400
        };

        for (String[] tier : tierMatrix) {
            String busType = tier[0];
            BigDecimal baseFare = new BigDecimal(tier[1]);
            BigDecimal expectedEffective = new BigDecimal(tier[2]);

            Route route = new Route("A", "B", baseFare, Route.RouteStatus.ACTIVE);
            Bus bus = new Bus("TEST-BUS-" + busType, 40, "Comfort", Bus.BusStatus.ACTIVE);
            bus.setBusType(busType);
            Schedule schedule = new Schedule(route, bus, null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), Schedule.ScheduleStatus.SCHEDULED);

            BigDecimal calculated = NTCFareCalculator.calculateSeatFare(schedule);
            assertEquals(expectedEffective, calculated, "Mismatch for tier: " + busType);
            assertEquals(expectedEffective, schedule.getEffectiveSeatFare(), "Schedule getter mismatch for tier: " + busType);

            // Total for 3 seats
            BigDecimal expectedTotal = expectedEffective.multiply(new BigDecimal(3));
            assertEquals(expectedTotal, NTCFareCalculator.calculateTotalFare(schedule, 3), "Total fare mismatch for tier: " + busType);
        }
    }

    @Test
    @DisplayName("Parcel Tariff: Base handling fee LKR 250.00 up to 1.0kg, 100.00/kg excess")
    void testParcelTariffCalculation() {
        // <= 1.0kg -> 250.00
        assertEquals(new BigDecimal("250.00"), ParcelService.calculateFee(new BigDecimal("0.5")));
        assertEquals(new BigDecimal("250.00"), ParcelService.calculateFee(new BigDecimal("1.0")));

        // 2.5kg -> 250 + (1.5 * 100) = 400.00
        assertEquals(new BigDecimal("400.00"), ParcelService.calculateFee(new BigDecimal("2.5")));

        // 10.0kg -> 250 + (9.0 * 100) = 1150.00
        assertEquals(new BigDecimal("1150.00"), ParcelService.calculateFee(new BigDecimal("10.0")));
    }
}
