package com.paystream.paystream;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessorHealth {

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final int FAILURE_THRESHOLD = 3;
    private static final int SUCCESS_THRESHOLD = 3;
    private static final int OPEN_TIMEOUT_SECONDS = 30;

    private final PaymentProcessor processor;
    private CircuitState state;
    private AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private LocalDateTime openedAt;

    public ProcessorHealth(PaymentProcessor processor) {
        this.processor = processor;
        this.state = CircuitState.CLOSED;
        this.consecutiveFailures.set(0);
        this.consecutiveSuccesses.set(0);
    }

    public PaymentProcessor getProcessor() {
        return processor;
    }

    public CircuitState getState() {
        return state;
    }

    public int getFailureCount() {
        return consecutiveFailures.get();
    }

    public int getSuccessCount() {
        return consecutiveSuccesses.get();
    }

    public boolean isAvailable() {
        if (state == CircuitState.CLOSED) return true;
        if (state == CircuitState.HALF_OPEN) return true;
        if (state == CircuitState.OPEN) {
            if (LocalDateTime.now().isAfter(openedAt.plusSeconds(OPEN_TIMEOUT_SECONDS))) {
                state = CircuitState.HALF_OPEN;
                consecutiveSuccesses.set(0);
                return true;
            }
            return false;
        }
        return false;
    }

    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
        //addResult(false);
        if (state == CircuitState.HALF_OPEN) {
            // any failure in HALF_OPEN → immediately back to OPEN
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        } else if (consecutiveFailures.get() >= FAILURE_THRESHOLD) {
            // normal CLOSED → OPEN transition
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        }
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        consecutiveSuccesses.incrementAndGet();
        //addResult(true);
        if (state == CircuitState.HALF_OPEN && consecutiveSuccesses.get() >= SUCCESS_THRESHOLD) {
            state = CircuitState.CLOSED;
        }
    }

}