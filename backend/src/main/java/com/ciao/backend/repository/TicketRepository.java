package com.ciao.backend.repository;
import com.ciao.backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketRepository extends JpaRepository<Ticket,Integer> {
    java.util.Optional<Ticket> findByReservationId(Integer id);
    java.util.Optional<Ticket> findByQrCode(String code);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Ticket t SET t.checkedInAt = :checkInTime WHERE t.id = :ticketId AND t.checkedInAt IS NULL")
    int atomicCheckIn(@org.springframework.data.repository.query.Param("ticketId") Integer ticketId, @org.springframework.data.repository.query.Param("checkInTime") java.time.LocalDateTime checkInTime);
}

