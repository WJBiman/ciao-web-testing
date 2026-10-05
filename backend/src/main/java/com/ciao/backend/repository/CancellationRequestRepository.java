package com.ciao.backend.repository;

import com.ciao.backend.entity.CancellationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, Integer> {
    List<CancellationRequest> findByReservationId(Integer reservationId);
    
    @Query("SELECT cr FROM CancellationRequest cr WHERE cr.reservation.id = :resId AND cr.status = 'PENDING'")
    Optional<CancellationRequest> findPendingByReservationId(@Param("resId") Integer resId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cr FROM CancellationRequest cr WHERE cr.id = :id")
    Optional<CancellationRequest> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cr FROM CancellationRequest cr WHERE cr.reservation.id = :resId AND cr.status = 'PENDING'")
    Optional<CancellationRequest> findPendingByReservationIdForUpdate(@Param("resId") Integer resId);

    List<CancellationRequest> findByStatusOrderByCreatedAtDesc(CancellationRequest.RequestStatus status);
}
