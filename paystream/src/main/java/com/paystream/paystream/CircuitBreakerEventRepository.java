package com.paystream.paystream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CircuitBreakerEventRepository extends JpaRepository<CircuitBreakerEvent, Long> {
    List<CircuitBreakerEvent> findTop50ByOrderByTimestampDesc();
}
