package com.paystream.paystream;

import org.springframework.stereotype.Service;

@Service
public class LoadTestService {

    private final PaymentService paymentService;

    public LoadTestService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public String runTest(int totalPayments) throws InterruptedException {
        int success = 0, failed = 0;
        PaymentRequest request = new PaymentRequest();
        request.setAmount("100");
        request.setCurrency("INR");
        for (int i = 0; i < totalPayments; i++) {
            String response = paymentService.processPayment(request);
            if (response.contains("SUCCESS")) success++;
            else failed++;
        }
        double rate = Math.round((success * 100.0 / totalPayments) * 10.0) / 10.0;
        return "Total: " + totalPayments +
                " | Success: " + success +
                " | Failed: " + failed +
                " | Rate: " + rate + "%";
    }
}