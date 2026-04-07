package com.paystream.paystream;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;

    public PaymentController(RouterService routerService) {
        this.routerService = routerService;
    }


    @PostMapping("/payment")
    public String processPayment(@RequestBody String body) {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) {
            return "All processors are down. Payment failed.";
        }
        return "Payment routed to: " + processor.name() + " | Request: " + body;
    }

    @PostMapping("/simulate/failure/{processorName}")
    public String simulateFailure(@PathVariable String processorName) {
        try {
            PaymentProcessor processor = PaymentProcessor.valueOf(processorName.toUpperCase());
            routerService.recordFailure(processor);
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            return processorName + " failure count: " + health.getFailureCount() +
                    " | Healthy: " + health.isHealthy();
        } catch (IllegalArgumentException e) {
            return "Unknown processor: " + processorName;
        }
    }

    @PostMapping("/simulate/success/{processorName}")
    public String simulateSuccess(@PathVariable String processorName) {
        try {
            PaymentProcessor processor = PaymentProcessor.valueOf(processorName.toUpperCase());
            routerService.recordSuccess(processor);
            return processorName + " recovered. Healthy: true";
        } catch (IllegalArgumentException e) {
            return "Unknown processor: " + processorName;
        }
    }
}