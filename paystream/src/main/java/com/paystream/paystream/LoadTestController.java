package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private final LoadTestService loadTestService;
    private final RouterService routerService;

    public LoadTestController(LoadTestService loadTestService, RouterService routerService) {
        this.loadTestService = loadTestService;
        this.routerService= routerService;
    }

    @PostMapping("/mode")
    public String setMode(@RequestParam String mode) {
        try {
            RouterService.RoutingMode routingMode = RouterService.RoutingMode.valueOf(mode.toUpperCase());
            routerService.setRoutingMode(routingMode);
            return "Routing mode set to: " + routingMode;
        } catch (IllegalArgumentException e) {
            return "Invalid mode. Use SMART or SINGLE_PROCESSOR";
        }
    }

    @GetMapping("/run")
    public String runLoadTest(@RequestParam int payments) throws InterruptedException {
        return loadTestService.runTest(payments);
    }
}