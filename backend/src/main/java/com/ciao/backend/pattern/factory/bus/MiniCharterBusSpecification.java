package com.ciao.backend.pattern.factory.bus;

import com.ciao.backend.entity.Bus;

public class MiniCharterBusSpecification implements BusSpecification {
    @Override
    public Bus create(String plateNumber) {
        Bus bus = new Bus(plateNumber, 28, "Compact charter seating, luggage compartment", Bus.BusStatus.ACTIVE);
        bus.setBusType("MINI_CHARTER");
        return bus;
    }
}
