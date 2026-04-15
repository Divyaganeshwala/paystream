package com.paystream.paystream;

public class ProcessorMetrics {
    private final double successRate;
    private final double averageLatency;

    public ProcessorMetrics(double successRate, double averageLatency) {
        this.successRate = successRate;
        this.averageLatency = averageLatency;
    }

    public double getSuccessRate() { return successRate; }
    public double getAverageLatency() { return averageLatency; }
}