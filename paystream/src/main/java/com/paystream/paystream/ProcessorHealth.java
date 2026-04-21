package com.paystream.paystream;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    private Consumer<String[]> onStateChange;

    public ProcessorHealth(PaymentProcessor processor) {
        this.processor = processor;
        this.state = CircuitState.CLOSED;
    }

    public PaymentProcessor getProcessor() { return processor; }
    public CircuitState getState() { return state; }
    public int getFailureCount() { return consecutiveFailures.get(); }
    public int getSuccessCount() { return consecutiveSuccesses.get(); }

    public void setOnStateChange(Consumer<String[]> callback) {
        this.onStateChange = callback;
    }

    private void notifyStateChange(CircuitState from, CircuitState to) {
        if (onStateChange != null && from != to) {
            onStateChange.accept(new String[]{processor.name(), from.name(), to.name()});
        }
    }

    public boolean isAvailable() {
        if (state == CircuitState.CLOSED) return true;
        if (state == CircuitState.HALF_OPEN) return true;
        if (state == CircuitState.OPEN) {
            if (LocalDateTime.now().isAfter(openedAt.plusSeconds(OPEN_TIMEOUT_SECONDS))) {
                CircuitState before = state;
                state = CircuitState.HALF_OPEN;
                consecutiveSuccesses.set(0);
                notifyStateChange(before, state);
                return true;
            }
            return false;
        }
        return false;
    }

    public void recordFailure() {
        CircuitState before = state;
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
        if (state == CircuitState.HALF_OPEN) {
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        } else if (consecutiveFailures.get() >= FAILURE_THRESHOLD) {
            state = CircuitState.OPEN;
            openedAt = LocalDateTime.now();
        }
        notifyStateChange(before, state);
    }

    public void recordSuccess() {
        CircuitState before = state;
        consecutiveFailures.set(0);
        consecutiveSuccesses.incrementAndGet();
        if (state == CircuitState.HALF_OPEN && consecutiveSuccesses.get() >= SUCCESS_THRESHOLD) {
            state = CircuitState.CLOSED;
        }
        notifyStateChange(before, state);
    }
}