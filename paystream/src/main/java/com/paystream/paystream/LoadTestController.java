package com.paystream.paystream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private static final int MAX_PAYMENTS = 200;
    private static final int MAX_THREADS = 20;

    private final LoadTestService loadTestService;
    private final LoadTestResultRepository loadTestResultRepository;

    public LoadTestController(LoadTestService loadTestService,
                              LoadTestResultRepository loadTestResultRepository) {
        this.loadTestService = loadTestService;
        this.loadTestResultRepository = loadTestResultRepository;
    }

    @GetMapping("/smart")
    public String runLoadTest(
            @RequestParam int payments,
            @RequestParam(defaultValue = "1") int threads) throws InterruptedException {

        return loadTestService.runConcurrentTest(payments, threads);
    }

    @GetMapping("/history")
    public java.util.List<LoadTestResult> getHistory() {
        return loadTestResultRepository.findTop10ByOrderByRanAtDesc();
    }
}