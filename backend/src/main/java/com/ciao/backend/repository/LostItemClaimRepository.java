package com.ciao.backend.repository;
import com.ciao.backend.entity.LostItemClaim;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LostItemClaimRepository extends JpaRepository<LostItemClaim,Integer> {
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE LostItemClaim c SET c.claimStatus = :newStatus, c.handledBy = :supervisor WHERE c.id = :claimId AND c.claimStatus = :expectedStatus")
    int atomicDecideClaim(@org.springframework.data.repository.query.Param("claimId") Integer claimId,
                          @org.springframework.data.repository.query.Param("expectedStatus") String expectedStatus,
                          @org.springframework.data.repository.query.Param("newStatus") String newStatus,
                          @org.springframework.data.repository.query.Param("supervisor") com.ciao.backend.entity.StaffProfile supervisor);
}

