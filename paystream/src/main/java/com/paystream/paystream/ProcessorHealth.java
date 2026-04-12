package com.paystream.paystream;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Queue;

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
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private LocalDateTime openedAt;
    private final Queue<Boolean> last100Results = new LinkedList<>();

    public ProcessorHealth(PaymentProcessor processor) {
        this.processor = processor;
        this.state = CircuitState.CLOSED;
        this.consecutiveFailures = 0;
        this.consecutiveSuccesses = 0;
    }

    public PaymentProcessor getProcessor() { return processor; }
    public CircuitState getState() { return state; }
    public int getFailureCount() { return consecutiveFailures; }
    public int getSuccessCount() { return consecutiveSuccesses; }

    public boolean isAvailable() {
        if (state == CircuitState.CLOSED) return true;
        if (state == CircuitState.HALF_OPEN) return true;
        if (state == CircuitState.OPEN) {
            if (LocalDateTime.now().isAfter(openedAt.plusSeconds(OPEN_TIMEOUT_SECONDS))) {
                state = CircuitState.HALF_OPEN;
                consecutiveSuccesses = 0;
                return true;
            }
            return false;
        }
        return false;
    }

    public void recordFailure() {
        consecutiveFailures++;
        consecutiveSuccesses = 0;
        addResult(false);
        if (state == CircuitState.HALF_OPEN) {
            // any failure in HALF_OPEN → immediately back to OPEN
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        } else if (consecutiveFailures >= FAILURE_THRESHOLD) {
            // normal CLOSED → OPEN transition
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        }
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
        consecutiveSuccesses++;
        addResult(true);
        if (state == CircuitState.HALF_OPEN && consecutiveSuccesses >= SUCCESS_THRESHOLD) {
            state = CircuitState.CLOSED;
        }
    }

    private void addResult(boolean success) {
        if (last100Results.size() >= 100) {
            last100Results.poll();
        }
        last100Results.offer(success);
    }

    public double getSuccessRate() {
        if (last100Results.isEmpty()) return 100.0;
        long successes = last100Results.stream()
                .filter(r -> r)
                .count();
        return (successes * 100.0) / last100Results.size();
    }
}