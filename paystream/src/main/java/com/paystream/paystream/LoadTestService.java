package com.paystream.paystream;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
public class LoadTestService {
    private final PaymentController paymentController;
    public LoadTestService(PaymentController paymentController){
        this.paymentController= paymentController;
    }

    public String runTest(int totalPayments) throws InterruptedException {
        int success = 0;
        int failed = 0;
        PaymentRequest request = new PaymentRequest();
        request.setAmount("0");
        request.setCurrency("INR");

        for (int i = 0; i < totalPayments; i++) {
            String response = paymentController.processPayment(request);
            if (response.contains("SUCCESS")) success++;
            else failed++;
        }
        return "Total: " + totalPayments +
                " | Success: " + success +
                " | Failed: " + failed +
                " | Rate: " + (success * 100.0 / totalPayments) + "%";

    }
}
