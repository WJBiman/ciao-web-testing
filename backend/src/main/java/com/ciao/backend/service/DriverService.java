package com.ciao.backend.service;

import com.ciao.backend.dto.DriverRequest;
import com.ciao.backend.entity.Driver;
import com.ciao.backend.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {

    @Autowired
    private DriverRepository driverRepository;

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Driver getDriverById(Integer id) {
        return driverRepository.findById(id).orElseThrow(() -> new RuntimeException("Driver not found with id: " + id));
    }

    public Driver createDriver(DriverRequest request) {
        String name = request.getDriverName() != null ? request.getDriverName().trim() : "";
        String license = request.getLicenseNumber() != null ? request.getLicenseNumber().trim().toUpperCase() : "";
        String contact = request.getContactNumber() != null ? request.getContactNumber().trim() : "";

        if (driverRepository.existsByLicenseNumber(license)) {
            throw new RuntimeException("Error: License number is already in use!");
        }

        Driver driver = new Driver(
                name,
                license,
                contact,
                request.getStatus() != null ? request.getStatus() : Driver.DriverStatus.AVAILABLE
        );

        return driverRepository.save(driver);
    }

    public Driver updateDriver(Integer id, DriverRequest request) {
        Driver driver = getDriverById(id);
        String name = request.getDriverName() != null ? request.getDriverName().trim() : "";
        String license = request.getLicenseNumber() != null ? request.getLicenseNumber().trim().toUpperCase() : "";
        String contact = request.getContactNumber() != null ? request.getContactNumber().trim() : "";

        if (!driver.getLicenseNumber().equalsIgnoreCase(license) && driverRepository.existsByLicenseNumber(license)) {
            throw new RuntimeException("Error: License number is already in use!");
        }

        driver.setDriverName(name);
        driver.setLicenseNumber(license);
        driver.setContactNumber(contact);
        
        if (request.getStatus() != null) {
            driver.setStatus(request.getStatus());
        }

        return driverRepository.save(driver);
    }

    public void deactivateDriver(Integer id) {
        Driver driver = getDriverById(id);
        driver.setStatus(Driver.DriverStatus.RETIRED);
        driverRepository.save(driver);
    }
}
