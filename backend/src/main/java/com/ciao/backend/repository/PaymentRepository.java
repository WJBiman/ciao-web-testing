package com.ciao.backend.repository;

import com.ciao.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    java.util.List<Payment> findByReservationId(Integer reservationId);
}

