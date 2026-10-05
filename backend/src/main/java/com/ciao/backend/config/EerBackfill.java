package com.ciao.backend.config;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.EerService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** Idempotent additive migration of legacy records; never deletes business data. */
@Component
public class EerBackfill implements ApplicationRunner {
    @Autowired UserRepository users;
    @Autowired BusRepository buses;
    @Autowired ReservationRepository reservations;
    @Autowired GroupBookingRepository groups;
    @Autowired PaymentRepository payments;
    @Autowired EerService eer;
    @Override @Transactional public void run(ApplicationArguments args) {
        users.findAll().forEach(eer::profile);
        buses.findAll().forEach(eer::syncSeats);
        reservations.findAll().forEach(eer::syncReservation);
        groups.findAll().forEach(eer::syncGroup);
        payments.findAll().forEach(eer::recordPayment);
    }
}
