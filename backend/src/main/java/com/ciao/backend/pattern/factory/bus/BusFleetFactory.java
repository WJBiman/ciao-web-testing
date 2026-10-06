package com.ciao.backend.pattern.factory.bus;

import com.ciao.backend.entity.Bus;

public final class BusFleetFactory {
    private BusFleetFactory() {}

    public static Bus createBus(String requestedSpecification, String plateNumber) {
        String normalized = requestedSpecification == null ? "" : requestedSpecification.trim().toUpperCase();
        BusSpecification specification;
        if (normalized.contains("MINI") || normalized.contains("CHARTER")) {
            specification = new MiniCharterBusSpecification();
        } else if (normalized.contains("SEMI")) {
            specification = new SemiLuxuryBusSpecification();
        } else {
            specification = new LuxuryAcBusSpecification();
        }
        return specification.create(plateNumber);
    }
}
