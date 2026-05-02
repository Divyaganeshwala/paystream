package com.paystream.paystream;

public class PaymentResponse {
    private final boolean success;
    private final String processor;
    private final String amount;
    private final int attempts;
    private final String message;
    private final boolean usedFallback;

    public PaymentResponse(boolean success, String processor, String amount,
                           int attempts, String message, boolean usedFallback) {
        this.success = success;
        this.processor = processor;
        this.amount = amount;
        this.attempts = attempts;
        this.message = message;
        this.usedFallback = usedFallback;
    }

    public boolean isUsedFallback() { return usedFallback; }
    public boolean isSuccess() { return success; }
    public String getProcessor() { return processor; }
    public String getAmount() { return amount; }
    public int getAttempts() { return attempts; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return (success ? "Payment SUCCESS on: " + processor : "Payment FAILED on all processors")
                + " | Amount: " + amount
                + " | Attempts: " + attempts;
    }
}