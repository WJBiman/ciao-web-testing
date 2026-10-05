package com.ciao.backend.repository;

import com.ciao.backend.entity.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Integer> {
    Optional<Parcel> findByTrackingId(String trackingId);
}
