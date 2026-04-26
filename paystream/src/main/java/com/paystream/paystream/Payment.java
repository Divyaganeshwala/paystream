package com.paystream.paystream;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String amount;
    private String currency;
    private String processor;
    private String status;
    private LocalDateTime createdAt;

    public Payment() {}

    public Payment(String amount, String currency, String processor, String status) {
        this.amount = amount;
        this.currency = currency;
        this.processor = processor;
        this.status = status;
        this.createdAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    public Long getId() { return id; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProcessor() { return processor; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}