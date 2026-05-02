package com.paystream.paystream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    long countByStatus(String status);
    long countByProcessor(String processor);
    List<Payment> findTop200ByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.usedFallback = true AND p.id IN (SELECT p2.id FROM Payment p2 ORDER BY p2.createdAt DESC LIMIT 200)")
    long countFallbacksInLast200();
}

