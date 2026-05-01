package com.paystream.paystream;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "load_test_results")
public class LoadTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int threads;
    private int total;
    private int success;
    private int failed;
    private double rate;
    private int retries;
    private long durationMs;
    private double throughput;
    private LocalDateTime ranAt;

    public LoadTestResult() {}

    public LoadTestResult(int threads, int total, int success, int failed,
                          double rate, int retries, long durationMs, double throughput) {
        this.threads = threads;
        this.total = total;
        this.success = success;
        this.failed = failed;
        this.rate = rate;
        this.retries = retries;
        this.durationMs = durationMs;
        this.throughput = throughput;
        this.ranAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    public Long getId() { return id; }
    public int getThreads() { return threads; }
    public int getTotal() { return total; }
    public int getSuccess() { return success; }
    public int getFailed() { return failed; }
    public double getRate() { return rate; }
    public int getRetries() { return retries; }
    public long getDurationMs() { return durationMs; }
    public double getThroughput() { return throughput; }
    public LocalDateTime getRanAt() { return ranAt; }
}