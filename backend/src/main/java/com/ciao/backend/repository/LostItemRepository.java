package com.ciao.backend.repository;

import com.ciao.backend.entity.LostItem;
import com.ciao.backend.entity.LostItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LostItemRepository extends JpaRepository<LostItem, Integer> {

    List<LostItem> findAllByOrderByCreatedAtDesc();

    List<LostItem> findByStatus(LostItemStatus status);

    @Query("SELECT l FROM LostItem l WHERE LOWER(l.itemDescription) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY l.createdAt DESC")
    List<LostItem> searchByDescription(@Param("query") String query);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE LostItem l SET l.status = :newStatus, l.handledBy = :supervisor WHERE l.id = :itemId AND l.status = :expectedStatus")
    int atomicTransitionItem(@Param("itemId") Integer itemId,
                             @Param("expectedStatus") LostItemStatus expectedStatus,
                             @Param("newStatus") LostItemStatus newStatus,
                             @Param("supervisor") com.ciao.backend.entity.StaffProfile supervisor);
}
