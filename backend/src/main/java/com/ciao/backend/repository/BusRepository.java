package com.ciao.backend.repository;

import com.ciao.backend.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Integer> {
    Optional<Bus> findByPlateNumber(String plateNumber);
    boolean existsByPlateNumber(String plateNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);
}
