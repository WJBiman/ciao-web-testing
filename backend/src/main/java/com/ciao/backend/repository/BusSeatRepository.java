package com.ciao.backend.repository;
import com.ciao.backend.entity.BusSeat;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BusSeatRepository extends JpaRepository<BusSeat,Integer> { 
    java.util.List<BusSeat> findByBusId(Integer id); 
    java.util.List<BusSeat> findByBusIdOrderBySeatNumberAsc(Integer id);
    java.util.Optional<BusSeat> findByBusIdAndSeatNumber(Integer busId, String seatNumber); 
}

