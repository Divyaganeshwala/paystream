package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

import static com.paystream.paystream.ProcessorHealth.CircuitState.CLOSED;
import static com.paystream.paystream.ProcessorHealth.CircuitState.HALF_OPEN;

@Service
public class RouterService {

    private final Map<PaymentProcessor, ProcessorHealth> healthMap = new HashMap<>();

    public RouterService() {
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            healthMap.put(processor, new ProcessorHealth(processor));
        }
    }

    public PaymentProcessor selectProcessor() {
        PaymentProcessor best = null;
        double bestScore = -1;

        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = healthMap.get(processor);
            if (health.isAvailable()) {

                if (health.getState() == ProcessorHealth.CircuitState.HALF_OPEN) return processor;
                double score = health.getSuccessRate();
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