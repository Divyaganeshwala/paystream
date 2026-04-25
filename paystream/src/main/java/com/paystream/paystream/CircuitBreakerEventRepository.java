package com.paystream.paystream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CircuitBreakerEventRepository extends JpaRepository<CircuitBreakerEvent, Long> {
}
