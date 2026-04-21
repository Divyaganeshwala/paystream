package com.paystream.paystream;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_breaker_events")
public class CircuitBreakerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processorName;
    private String fromState;
    private String toState;
    private LocalDateTime timestamp;

    public CircuitBreakerEvent() {}

    public CircuitBreakerEvent(String processorName, String fromState, String toState) {
        this.processorName = processorName;
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getProcessorName() { return processorName; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public LocalDateTime getTimeStamp() { return timestamp; }
}
