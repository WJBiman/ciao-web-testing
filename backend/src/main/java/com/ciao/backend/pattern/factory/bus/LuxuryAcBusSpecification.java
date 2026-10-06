package com.ciao.backend.pattern.factory.bus;

import com.ciao.backend.entity.Bus;

public class LuxuryAcBusSpecification implements BusSpecification {
    @Override
    public Bus create(String plateNumber) {
        Bus bus = new Bus(plateNumber, 42, "Air conditioning, reclining seats, USB charging", Bus.BusStatus.ACTIVE);
        bus.setBusType("LUXURY_AC");
        return bus;
    }
}
