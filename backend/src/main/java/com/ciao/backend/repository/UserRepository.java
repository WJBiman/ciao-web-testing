package com.ciao.backend.repository;

import com.ciao.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    java.util.List<User> findByPhoneIn(java.util.Collection<String> phones);
    boolean existsByPhoneIn(java.util.Collection<String> phones);
    Boolean existsByEmailIgnoreCase(String email);
    Boolean existsByPhone(String phone);
    Boolean existsByNic(String nic);
}
