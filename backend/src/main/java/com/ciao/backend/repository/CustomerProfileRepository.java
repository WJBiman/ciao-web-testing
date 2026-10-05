package com.ciao.backend.repository;
import com.ciao.backend.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile,Integer> { java.util.Optional<CustomerProfile> findByUserId(Integer userId); }

