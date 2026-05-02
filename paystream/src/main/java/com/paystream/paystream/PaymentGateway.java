package com.paystream.paystream;

public interface PaymentGateway {
    PaymentProcessor getProcessor();
    boolean processPayment(String amount, String currency);
}