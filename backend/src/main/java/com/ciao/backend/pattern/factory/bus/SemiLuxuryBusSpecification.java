package com.ciao.backend.pattern.factory.bus;

import com.ciao.backend.entity.Bus;

public class SemiLuxuryBusSpecification implements BusSpecification {
    @Override
    public Bus create(String plateNumber) {
        Bus bus = new Bus(plateNumber, 49, "Reclining seats, overhead luggage racks", Bus.BusStatus.ACTIVE);
        bus.setBusType("SEMI_LUXURY");
        return bus;
    }
}
