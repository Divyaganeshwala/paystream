package com.paystream.paystream;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;

    public PaymentController(RouterService routerService, PaymentRepository paymentRepository) {
        this.routerService = routerService;
        this.paymentRepository= paymentRepository;
    }


    @PostMapping("/payment")
    public String processPayment(@RequestBody PaymentRequest request) {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) {
            return "All processors are down. Payment failed.";
        }
        Payment payment = new Payment(request.getAmount(), request.getCurrency(), processor.name(), "SUCCESS");
        paymentRepository.save(payment);
        return "Payment routed to: " + processor.name() + " | Amount: " + request.getAmount();
    }

    @GetMapping("/payments")
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
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