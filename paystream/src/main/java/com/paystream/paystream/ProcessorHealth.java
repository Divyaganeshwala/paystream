package com.paystream.paystream;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ProcessorHealth {

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final int SUCCESS_THRESHOLD = 3;
    private static final int OPEN_TIMEOUT_SECONDS = 30;
    private static final int CONSECUTIVE_THRESHOLD = 3;
    private static final double SLA = 0.95;
    private static final double TOLERANCE = 0.10;
    private static final int MIN_REQUESTS_PER_MINUTE = 10;

    private final PaymentProcessor processor;
    private final ReentrantLock lock = new ReentrantLock();
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

    private void notifyStateChange(CircuitState from, CircuitState to, String reason) {
        if (onStateChange != null && from != to) {
            onStateChange.accept(new String[]{processor.name(), from.name(), to.name(), reason});
        }
    }

    public boolean isAvailable() {
        lock.lock();
        try {
            if (state == CircuitState.CLOSED) return true;
            if (state == CircuitState.HALF_OPEN) return true;
            if (state == CircuitState.OPEN) {
                if (LocalDateTime.now().isAfter(openedAt.plusSeconds(OPEN_TIMEOUT_SECONDS))) {
                    CircuitState before = state;
                    state = CircuitState.HALF_OPEN;
                    consecutiveSuccesses.set(0);
                    notifyStateChange(before, state, "30s timeout elapsed, probing");
                    return true;
                }
                return false;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure() {
        recordFailure(new ProcessorMetrics(100.0, 0.0), 0);
    }

    public void recordSuccess() {
        lock.lock();
        try {
            CircuitState before = state;
            consecutiveFailures.set(0);
            consecutiveSuccesses.incrementAndGet();
            if (state == CircuitState.HALF_OPEN && consecutiveSuccesses.get() >= SUCCESS_THRESHOLD) {
                state = CircuitState.CLOSED;
                notifyStateChange(before, state, "Recovery: 3 consecutive successes");
            } else {
                notifyStateChange(before, state, "");
            }
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure(ProcessorMetrics metrics, long lastMinuteCount) {
        lock.lock();
        try {
            if (state == CircuitState.OPEN) return;
            CircuitState before = state;
            consecutiveFailures.incrementAndGet();
            consecutiveSuccesses.set(0);

            if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.OPEN;
                openedAt = LocalDateTime.now();
                notifyStateChange(before, state, "Failed during recovery (HALF_OPEN)");
            } else if (consecutiveFailures.get() >= CONSECUTIVE_THRESHOLD) {
                state = CircuitState.OPEN;
                openedAt = LocalDateTime.now();
                notifyStateChange(before, state,
                        consecutiveFailures.get() + " consecutive failures");
            } else if (lastMinuteCount >= MIN_REQUESTS_PER_MINUTE &&
                    metrics.getSuccessRate() < (SLA - TOLERANCE) * 100) {
                state = CircuitState.OPEN;
                openedAt = LocalDateTime.now();
                notifyStateChange(before, state,
                        String.format("SLA breach: %.1f%% < 85%% over %d requests",
                                metrics.getSuccessRate(), lastMinuteCount));
            } else {
                notifyStateChange(before, state, "");
            }
        } finally {
            lock.unlock();
        }
    }
}