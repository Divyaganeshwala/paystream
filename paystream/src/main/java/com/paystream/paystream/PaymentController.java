package com.paystream.paystream;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RedisService redisService;
    private final PaymentService paymentService;
    private final CircuitBreakerEventRepository circuitBreakerEventRepository;
    private final RoutingLogRepository routingLogRepository;

    public PaymentController(RouterService routerService, PaymentRepository paymentRepository, RedisService redisService, PaymentService paymentService, CircuitBreakerEventRepository circuitBreakerEventRepository, RoutingLogRepository routingLogRepository) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.redisService= redisService;
        this.paymentService= paymentService;
        this.circuitBreakerEventRepository =circuitBreakerEventRepository;
        this.routingLogRepository= routingLogRepository;
    }

    @PostMapping("/payment")
    public String processPayment(@RequestBody PaymentRequest request) throws InterruptedException {
        return paymentService.processPayment(request);
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

            ProcessorMetrics metrics = redisService.getMetrics(processor);
            double score = (metrics.getSuccessRate() * 0.6) + (1000.0 / (metrics.getAverageLatency() + 1) * 0.4);

            sb.append(processor.name())
                    .append(" → state: ").append(health.getState())
                    .append(" | consecutiveFailures: ").append(health.getFailureCount())
                    .append(" | consecutiveSuccesses: ").append(health.getSuccessCount())
                    .append(" | avgLatency: ")
                    .append(String.format("%.0f", metrics.getAverageLatency())).append("ms")
                    .append(" | successRate: ")
                    .append(String.format("%.1f", metrics.getSuccessRate())).append("%")
                    .append(" | score: ").append(score)
                    .append(" | totalHandled: ").append(paymentRepository.countByProcessorAndAmountNot(processor.name(), "0"))
                    .append("\n");
        }
        return sb.toString();
    }

    @GetMapping("/events")
    public List<CircuitBreakerEvent> getEvents() {
        return circuitBreakerEventRepository.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = paymentRepository.countByAmountNot("0");
        long success = paymentRepository.countByStatusAndAmountNot("SUCCESS", "0");
        long failed = paymentRepository.countByStatusAndAmountNot("FAILED", "0");
        double rate = total == 0 ? 0 : (success * 100.0) / total;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("successRate", Math.round(rate * 10.0) / 10.0);
        return stats;
    }

    @GetMapping("/payments/{id}/routing")
    public List<RoutingLog> getRoutingLog(@PathVariable Long id) {
        return routingLogRepository.findByPaymentId(id);
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
}