package com.ciao.backend.pattern.factory.bus;

import com.ciao.backend.entity.Bus;

public interface BusSpecification {
    Bus create(String plateNumber);
}
