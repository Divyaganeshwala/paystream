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
    public String runSmartTest(@RequestParam int payments) throws InterruptedException {
        return loadTestService.runSmartTest(payments);
    }

    @GetMapping("/single")
    public String runSingleProcessorTest(@RequestParam int payments) throws InterruptedException {
        return loadTestService.runSingleProcessorTest(payments);
    }
}