package com.paystream.paystream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    long countByStatus(String status);
    long countByProcessor(String processor);
}