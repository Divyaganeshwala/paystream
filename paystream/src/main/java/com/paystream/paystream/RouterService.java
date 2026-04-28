package com.paystream.paystream;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouterService {

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
        this.redisService = redisService;
        this.eventRepository = eventRepository;
    }

    public Map<PaymentProcessor, ProcessorHealth> getHealthMap() { return healthMap; }

    public double calculateScore(ProcessorMetrics metrics) {
        double latency = metrics.getAverageLatency();
        double latencyScore = 100.0 * (1.0 - latency / (latency + 1000.0));
        return (metrics.getSuccessRate() * 0.6) + (latencyScore * 0.4);
    }

    public Map<String, Double> getScoreSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            snapshot.put(processor.name(), calculateScore(redisService.getMetrics(processor)));
        }
        return snapshot;
    }

    public PaymentProcessor selectProcessor(List<PaymentProcessor> exclude) {
        PaymentProcessor best = null;
        double bestScore = -1;

        for (PaymentProcessor processor : PaymentProcessor.values()) {
            if (exclude.contains(processor)) continue;
            ProcessorHealth health = healthMap.get(processor);
            if (!health.isAvailable()) continue;
            if (health.getState() == ProcessorHealth.CircuitState.HALF_OPEN) return processor;
            double score = calculateScore(redisService.getMetrics(processor));
            if (score > bestScore) {
                bestScore = score;
                best = processor;
            }
        }
        return best;
    }

    public PaymentProcessor selectProcessor() {
        return selectProcessor(new ArrayList<>());
    }

    public void recordSuccess(PaymentProcessor processor) {
        healthMap.get(processor).recordSuccess();
    }

    public void recordFailure(PaymentProcessor processor) {
        ProcessorMetrics metrics = redisService.getMetrics(processor);
        long lastMinuteCount = redisService.getLastMinuteCount(processor);
        healthMap.get(processor).recordFailure(metrics, lastMinuteCount);
    }
}