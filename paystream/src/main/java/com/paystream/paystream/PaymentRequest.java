package com.paystream.paystream;

public class PaymentRequest {
    private String amount;
    private String currency;

    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
}