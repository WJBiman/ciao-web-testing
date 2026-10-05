package com.ciao.backend.repository;
import com.ciao.backend.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BranchRepository extends JpaRepository<Branch,Integer> {
    Optional<Branch> findByManager_Id(Integer managerId);
}

