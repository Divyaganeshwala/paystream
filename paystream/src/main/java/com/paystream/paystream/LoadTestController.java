package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @GetMapping("/run")
    public String runLoadTest(@RequestParam int payments) throws InterruptedException {
        return loadTestService.runTest(payments);
    }
}