package com.paystream.paystream;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

@Service
public class RouterService {

    private final Map<PaymentProcessor, ProcessorHealth> healthMap = new HashMap<>();

    public RouterService() {
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            healthMap.put(processor, new ProcessorHealth(processor));
        }
    }

    public PaymentProcessor selectProcessor() {
        for (PaymentProcessor processor : PaymentProcessor.values()) {
            if (healthMap.get(processor).isHealthy()) {
                return processor;
            }
        }
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