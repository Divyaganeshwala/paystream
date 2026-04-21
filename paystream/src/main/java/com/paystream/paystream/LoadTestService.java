package com.paystream.paystream;

import org.springframework.stereotype.Service;

@Service
public class LoadTestService {

    private final PaymentService paymentService;

    public LoadTestService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public String runSmartTest(int totalPayments) throws InterruptedException {
        int success = 0, failed = 0;
        PaymentRequest request = new PaymentRequest();
        request.setAmount("100");
        request.setCurrency("INR");
        for (int i = 0; i < totalPayments; i++) {
            String response = paymentService.processPayment(request);
            if (response.contains("SUCCESS")) success++;
            else failed++;
        }
        return buildResult("SMART", totalPayments, success, failed);
    }

    public String runSingleProcessorTest(int totalPayments) throws InterruptedException {
        int success = 0, failed = 0;
        PaymentRequest request = new PaymentRequest();
        request.setAmount("100");
        request.setCurrency("INR");
        for (int i = 0; i < totalPayments; i++) {
            String response = paymentService.processSingleProcessorPayment(request);
            if (response.contains("SUCCESS")) success++;
            else failed++;
        }
        return buildResult("SINGLE_PROCESSOR", totalPayments, success, failed);
    }

    private String buildResult(String mode, int total, int success, int failed) {
        double rate = Math.round((success * 100.0 / total) * 10.0) / 10.0;
        return mode + " | Total: " + total +
                " | Success: " + success +
                " | Failed: " + failed +
                " | Rate: " + rate + "%";
    }
}