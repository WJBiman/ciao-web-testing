package com.ciao.backend.repository;
import com.ciao.backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingRepository extends JpaRepository<Booking,Integer> {  }

