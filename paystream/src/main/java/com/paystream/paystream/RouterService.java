package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class RouterService {

    public enum RoutingMode {
        SMART,
        SINGLE_PROCESSOR
    }

    private final Map<PaymentProcessor, ProcessorHealth> healthMap = new HashMap<>();
    private final RedisService redisService;
    private final CircuitBreakerEventRepository eventRepository;

    public RouterService(RedisService redisService, CircuitBreakerEventRepository eventRepository) {
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = new ProcessorHealth(processor);
            health.setOnStateChange(event ->
                    eventRepository.save(new CircuitBreakerEvent(event[0], event[1], event[2]))
            );
            healthMap.put(processor, health);
        }
        this.redisService= redisService;
        this.eventRepository= eventRepository;
    }

    private RoutingMode currentMode = RoutingMode.SMART;
    public void setRoutingMode(RoutingMode mode) {
        this.currentMode = mode;
    }

    public RoutingMode getCurrentMode() { return currentMode; }

    public Map<String, Double> getScoreSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorMetrics metrics = redisService.getMetrics(processor);
            double latency = metrics.getAverageLatency();
            double score = latency == 0 ? metrics.getSuccessRate() :
                    (metrics.getSuccessRate() * 0.6) + (1000.0 / (latency + 1) * 0.4);
            snapshot.put(processor.name(), score);
        }
        return snapshot;
    }

    public PaymentProcessor selectProcessor() {
        if(currentMode.equals(RoutingMode.SINGLE_PROCESSOR)) return PaymentProcessor.RAZORPAY;

        PaymentProcessor best = null;
        double bestScore = -1;

        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = healthMap.get(processor);
            if (health.isAvailable()) {

                if (health.getState() == ProcessorHealth.CircuitState.HALF_OPEN) return processor;
                ProcessorMetrics metrics = redisService.getMetrics(processor);
                double score = (metrics.getSuccessRate() * 0.6) + (1000.0 / (metrics.getAverageLatency() + 1) * 0.4);
                if (score > bestScore) {
                    bestScore = score;
                    best = processor;
                }
            }
        }
        return best;
    }

    public void recordSuccess(PaymentProcessor processor) {
        healthMap.get(processor).recordSuccess();
    }

    public void recordFailure(PaymentProcessor processor) {
        healthMap.get(processor).recordFailure();
    }

    public Map<PaymentProcessor, ProcessorHealth> getHealthMap() {
        return healthMap;
    }
}