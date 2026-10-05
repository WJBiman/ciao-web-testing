package com.ciao.backend.service;

import com.ciao.backend.dto.BusRequest;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.repository.BusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusService {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private EerService eerService;

    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    public List<Bus> getActiveBuses() {
        return busRepository.findAll().stream()
                .filter(b -> b.getStatus() == Bus.BusStatus.ACTIVE)
                .toList();
    }

    public Bus getBusById(Integer id) {
        return busRepository.findById(id).orElseThrow(() -> new RuntimeException("Bus not found with id: " + id));
    }

    public Bus createBus(BusRequest request) {
        String plate = request.getPlateNumber() != null ? request.getPlateNumber().trim().toUpperCase() : "";
        String amenities = request.getAmenities() != null ? request.getAmenities().trim() : "";

        if (busRepository.existsByPlateNumber(plate)) {
            throw new RuntimeException("Error: Plate number is already in use!");
        }

        Bus bus = new Bus(
                plate,
                request.getCapacity(),
                amenities,
                request.getStatus() != null ? request.getStatus() : Bus.BusStatus.ACTIVE
        );

        Bus saved = busRepository.save(bus);
        eerService.syncSeats(saved);
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public Bus updateBus(Integer id, BusRequest request) {
        Bus bus = busRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Bus not found with id: " + id));
        String plate = request.getPlateNumber() != null ? request.getPlateNumber().trim().toUpperCase() : "";
        String amenities = request.getAmenities() != null ? request.getAmenities().trim() : "";

        if (!bus.getPlateNumber().equalsIgnoreCase(plate) && busRepository.existsByPlateNumber(plate)) {
            throw new RuntimeException("Error: Plate number is already in use!");
        }

        if (request.getCapacity() != null && request.getCapacity() < bus.getCapacity()) {
            // Check if any active future reservations exist on this bus for seat numbers > new capacity
            int newCap = request.getCapacity();
            boolean hasConflictingReservations = eerService.hasActiveReservationsExceedingCapacity(bus.getId(), newCap);
            if (hasConflictingReservations) {
                throw new RuntimeException("Cannot reduce bus capacity to " + newCap + ": There are confirmed upcoming reservations on seats exceeding this capacity. Reassign those passengers first.");
            }
        }

        bus.setPlateNumber(plate);
        bus.setCapacity(request.getCapacity());
        bus.setAmenities(amenities);
        if (request.getStatus() != null) {
            bus.setStatus(request.getStatus());
        }

        Bus saved = busRepository.save(bus);
        eerService.syncSeats(saved);
        return saved;
    }

    public void deactivateBus(Integer id) {
        Bus bus = getBusById(id);
        bus.setStatus(Bus.BusStatus.RETIRED);
        busRepository.save(bus);
    }
}
