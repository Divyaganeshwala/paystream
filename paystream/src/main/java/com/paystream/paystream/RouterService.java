package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class RouterService {

    private final Map<PaymentProcessor, ProcessorHealth> healthMap = new HashMap<>();

    public RouterService() {
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            healthMap.put(processor, new ProcessorHealth(processor));
        }
    }

    public PaymentProcessor selectProcessor() {
        // Priority 1: HALF_OPEN processor gets a test payment
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = healthMap.get(processor);
            if (health.getState() == ProcessorHealth.CircuitState.HALF_OPEN) {
                return processor;
            }
        }

        // Priority 2: pick first CLOSED processor
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            ProcessorHealth health = healthMap.get(processor);
            if (health.getState() == ProcessorHealth.CircuitState.CLOSED) {
                return processor;
            }
        }

        // All processors are OPEN
        return null;
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