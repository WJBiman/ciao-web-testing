package com.ciao.backend.repository;
import com.ciao.backend.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RouteStopRepository extends JpaRepository<RouteStop,Integer> { java.util.List<RouteStop> findByRouteIdOrderBySequenceNumber(Integer id); }

