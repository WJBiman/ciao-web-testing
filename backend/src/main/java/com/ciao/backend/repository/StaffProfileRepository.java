package com.ciao.backend.repository;
import com.ciao.backend.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffProfileRepository extends JpaRepository<StaffProfile,Integer> { 
    java.util.Optional<StaffProfile> findByUserId(Integer userId); 
    java.util.List<StaffProfile> findByStaffType(String staffType);
}

