package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private final LoadTestService loadTestService;
    private final LoadTestResultRepository loadTestResultRepository;

    public LoadTestController(LoadTestService loadTestService,
                              LoadTestResultRepository loadTestResultRepository) {
        this.loadTestService = loadTestService;
        this.loadTestResultRepository = loadTestResultRepository;
    }

    @GetMapping("/smart")
    public String runLoadTest(@RequestParam int payments,
                              @RequestParam(defaultValue = "1") int threads)
            throws InterruptedException {
        return loadTestService.runConcurrentTest(payments, threads);
    }

    @GetMapping("/history")
    public List<LoadTestResult> getHistory() {
        return loadTestResultRepository.findTop10ByOrderByRanAtDesc();
    }
}