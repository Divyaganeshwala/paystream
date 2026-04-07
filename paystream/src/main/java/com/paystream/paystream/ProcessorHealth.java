package com.paystream.paystream;

public class ProcessorHealth {
    private PaymentProcessor processor;
    private boolean healthy;
    private int failureCount;

    public ProcessorHealth(PaymentProcessor processor) {
        this.processor = processor;
        this.healthy = true;
        this.failureCount = 0;
    }

    public PaymentProcessor getProcessor() { return processor; }
    public boolean isHealthy() { return healthy; }
    public int getFailureCount() { return failureCount; }

    public void recordFailure() {
        failureCount++;
        if (failureCount >= 3) {
            healthy = false;
        }
    }

    public void recordSuccess() {
        failureCount = 0;
        healthy = true;
    }
}