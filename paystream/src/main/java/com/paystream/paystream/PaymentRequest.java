package com.paystream.paystream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PaymentRequest {

    @NotBlank(message = "Amount is required")
    @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$", message = "Amount must be a positive number")
    private String amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code like INR or USD")
    private String currency;

    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
}