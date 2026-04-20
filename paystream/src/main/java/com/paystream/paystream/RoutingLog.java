package com.paystream.paystream;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "routing_log")
public class RoutingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;
    private String processorName;
    private double score;
    private String state;
    private boolean wasSelected;
    private LocalDateTime timestamp;

    public RoutingLog() {}

    public RoutingLog(Long paymentId, String processorName, double score, String state, boolean wasSelected) {
        this.paymentId = paymentId;
        this.processorName = processorName;
        this.score = score;
        this.state = state;
        this.wasSelected = wasSelected;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public String getProcessorName() { return processorName; }
    public double getScore() { return score; }
    public String getState() { return state; }
    public boolean isWasSelected() { return wasSelected; }
    public LocalDateTime getTimestamp() { return timestamp; }
}