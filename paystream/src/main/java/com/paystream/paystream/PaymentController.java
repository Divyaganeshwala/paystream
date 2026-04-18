package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RazorpayProcessor razorpayProcessor;
    private final RedisService redisService;

    private final Random random = new Random();

    public PaymentController(RouterService routerService, PaymentRepository paymentRepository, RazorpayProcessor razorpayProcessor, RedisService redisService) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.razorpayProcessor= razorpayProcessor;
        this.redisService= redisService;
    }

    @PostMapping("/payment")
    public String processPayment(@RequestBody PaymentRequest request) throws InterruptedException {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) {
            return "All processors are down. Payment failed.";
        }

        long start = System.currentTimeMillis();
        boolean success;
        if(processor == PaymentProcessor.RAZORPAY){
            success= razorpayProcessor.processPayment(request.getAmount(), request.getCurrency());
        }
        else {
            Thread.sleep(150 + random.nextInt(200)); // realistic 150-350ms latency
            success = random.nextInt(10) != 0;
        }
        long latencyMs = System.currentTimeMillis() - start;

        String status = success ? "SUCCESS" : "FAILED";

        redisService.recordPaymentResult(processor, success, latencyMs);

        if (success) {
            routerService.recordSuccess(processor);
        } else {
            routerService.recordFailure(processor);
        }

        Payment payment = new Payment(
                request.getAmount(), request.getCurrency(), processor.name(), status
        );
        paymentRepository.save(payment);

        return "Payment " + status + " on: " + processor.name()
                + " | Amount: " + request.getAmount();
    }

    @GetMapping("/payments")
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @GetMapping("/health")
    public String getHealth() {

        StringBuilder sb = new StringBuilder();
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            health.isAvailable();
            sb.append(processor.name())
                    .append(" → state: ").append(health.getState())
                    .append(" | consecutiveFailures: ").append(health.getFailureCount())
                    .append(" | consecutiveSuccesses: ").append(health.getSuccessCount())
                    .append(" | successRate: ")
                    .append(String.format("%.1f", redisService.getMetrics(processor).getSuccessRate())).append("%")
                    .append("\n");
        }
        return sb.toString();
    }

    @PostMapping("/simulate/failure/{processorName}")
    public String simulateFailure(@PathVariable String processorName) {
        try {
            PaymentProcessor processor = PaymentProcessor.valueOf(processorName.toUpperCase());
            routerService.recordFailure(processor);
            Payment payment = new Payment("0", "INR", processorName.toUpperCase(), "FAILED");
            paymentRepository.save(payment);
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            redisService.recordPaymentResult(processor, false, 0);
            return processorName + " failure recorded"
                    + " | State: " + health.getState()
                    + " | consecutiveFailures: " + health.getFailureCount()
                    + " | successRate: "
                    + String.format("%.1f", redisService.getMetrics(processor).getSuccessRate()) + "%";
        } catch (IllegalArgumentException e) {
            return "Unknown processor: " + processorName;
        }
    }

    @PostMapping("/simulate/success/{processorName}")
    public String simulateSuccess(@PathVariable String processorName) {
        try {
            PaymentProcessor processor = PaymentProcessor.valueOf(processorName.toUpperCase());
            routerService.recordSuccess(processor);
            Payment payment = new Payment("0", "INR", processorName.toUpperCase(), "RECOVERED");
            paymentRepository.save(payment);
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            redisService.recordPaymentResult(processor, true, 0);
            return processorName + " success recorded"
                    + " | State: " + health.getState()
                    + " | consecutiveSuccesses: " + health.getSuccessCount()
                    + " | successRate: "
                    + String.format("%.1f", redisService.getMetrics(processor).getSuccessRate()) + "%";
        } catch (IllegalArgumentException e) {
            return "Unknown processor: " + processorName;
        }
    }
}