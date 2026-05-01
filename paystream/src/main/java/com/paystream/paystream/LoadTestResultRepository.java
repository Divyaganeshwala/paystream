package com.paystream.paystream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoadTestResultRepository extends JpaRepository<LoadTestResult, Long> {
    List<LoadTestResult> findTop10ByOrderByRanAtDesc();
}