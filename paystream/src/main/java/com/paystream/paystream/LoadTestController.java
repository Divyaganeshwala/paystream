package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @GetMapping("/smart")
    public String runLoadTest(@RequestParam int payments,
                              @RequestParam(defaultValue = "1") int threads)
            throws InterruptedException {
        return loadTestService.runConcurrentTest(payments, threads);
    }
}