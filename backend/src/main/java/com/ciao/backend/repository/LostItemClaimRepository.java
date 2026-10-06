package com.ciao.backend.repository;
import com.ciao.backend.entity.LostItemClaim;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LostItemClaimRepository extends JpaRepository<LostItemClaim,Integer> {
    java.util.List<LostItemClaim> findByItemId(Integer itemId);
    java.util.List<LostItemClaim> findByItemIdAndClaimStatus(Integer itemId, String claimStatus);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM LostItemClaim c LEFT JOIN FETCH c.item i LEFT JOIN FETCH i.bus LEFT JOIN FETCH i.route WHERE c.claimant.id = :claimantId ORDER BY c.claimDate DESC")
    java.util.List<LostItemClaim> findWithDetailsByClaimantId(@org.springframework.data.repository.query.Param("claimantId") Integer claimantId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM LostItemClaim c LEFT JOIN FETCH c.item i LEFT JOIN FETCH i.bus LEFT JOIN FETCH i.route LEFT JOIN FETCH c.claimant ORDER BY c.claimDate DESC")
    java.util.List<LostItemClaim> findWithDetailsAll();

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE LostItemClaim c SET c.claimStatus = :newStatus, c.handledBy = :supervisor WHERE c.id = :claimId AND c.claimStatus = :expectedStatus")
    int atomicDecideClaim(@org.springframework.data.repository.query.Param("claimId") Integer claimId,
                          @org.springframework.data.repository.query.Param("expectedStatus") String expectedStatus,
                          @org.springframework.data.repository.query.Param("newStatus") String newStatus,
                          @org.springframework.data.repository.query.Param("supervisor") com.ciao.backend.entity.StaffProfile supervisor);
}

