package com.ciao.backend.repository;
import com.ciao.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification,Integer> { java.util.List<Notification> findByUserIdOrderBySentAtDesc(Integer userId); }

