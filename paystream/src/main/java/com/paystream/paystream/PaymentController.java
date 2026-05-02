package com.paystream.paystream;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RedisService redisService;
    private final PaymentService paymentService;
    private final CircuitBreakerEventRepository circuitBreakerEventRepository;
    private final RoutingLogRepository routingLogRepository;

    public PaymentController(RouterService routerService,
                             PaymentRepository paymentRepository,
                             RedisService redisService,
                             PaymentService paymentService,
                             CircuitBreakerEventRepository circuitBreakerEventRepository,
                             RoutingLogRepository routingLogRepository) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.redisService = redisService;
        this.paymentService = paymentService;
        this.circuitBreakerEventRepository = circuitBreakerEventRepository;
        this.routingLogRepository = routingLogRepository;
    }

    @PostMapping("/payment")
    public String processPayment(@Valid @RequestBody PaymentRequest request) throws InterruptedException {
        return paymentService.processPayment(request).toString();
    }

    @GetMapping("/payments")
    public List<Payment> getAllPayments() {
        return paymentRepository.findTop200ByOrderByCreatedAtDesc();
    }

    @GetMapping("/payments/{id}/routing")
    public List<RoutingLog> getRoutingLog(@PathVariable Long id) {
        return routingLogRepository.findByPaymentId(id);
    }

    @GetMapping("/health")
    public List<Map<String, Object>> getHealth() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            health.isAvailable();
            ProcessorMetrics metrics = redisService.getMetrics(processor);
            double score = routerService.calculateScore(metrics);

            Map<String, Object> map = new HashMap<>();
            map.put("name", processor.name());
            map.put("state", health.getState().name());
            map.put("consecutiveFailures", health.getFailureCount());
            map.put("consecutiveSuccesses", health.getSuccessCount());
            map.put("avgLatency", metrics.getAverageLatency());
            map.put("successRate", metrics.getSuccessRate());
            map.put("score", score);
            result.add(map);
        }
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = paymentRepository.count();
        long success = paymentRepository.countByStatus("SUCCESS");
        long failed = paymentRepository.countByStatus("FAILED");
        double rate = total == 0 ? 0 : (success * 100.0) / total;

        List<Payment> last200 = paymentRepository.findTop200ByOrderByCreatedAtDesc();
        long fallbacks = last200.stream().filter(Payment::isUsedFallback).count();
        double fallbackRate = last200.isEmpty() ? 0 :
                Math.round((fallbacks * 100.0 / last200.size()) * 10.0) / 10.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("successRate", Math.round(rate * 10.0) / 10.0);
        stats.put("fallbackRate", fallbackRate);
        stats.put("fallbackWindow", last200.size());

        Map<String, Long> handledPerProcessor = new HashMap<>();
        for (PaymentProcessor p : PaymentProcessor.values()) {
            handledPerProcessor.put(p.name(), paymentRepository.countByProcessor(p.name()));
        }
        stats.put("handledPerProcessor", handledPerProcessor);
        return stats;
    }

    @GetMapping("/events")
    public List<CircuitBreakerEvent> getEvents() {
        return circuitBreakerEventRepository.findTop50ByOrderByTimestampDesc();
    }

    @PostMapping("/simulate/failure/{processorName}")
    public String simulateFailure(@PathVariable String processorName) {
        try {
            PaymentProcessor processor = PaymentProcessor.valueOf(processorName.toUpperCase());
            routerService.recordFailure(processor);
            ProcessorHealth health = routerService.getHealthMap().get(processor);
            return processorName + " failure recorded"
                    + " | State: " + health.getState()
                    + " | consecutiveFailures: " + health.getFailureCount();
        } catch (IllegalArgumentException e) {
            return "Unknown processor: " + processorName;
        }
    }

    @PostMapping("/redis/flush")
    public String flushRedis() {
        redisService.flushAll();
        return "Redis flushed. All processor scores reset.";
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String error = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body("Validation failed: " + error);
    }
}