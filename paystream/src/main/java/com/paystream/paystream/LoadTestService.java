package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadTestService {

    private final PaymentService paymentService;

    public LoadTestService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public String runTest(int totalPayments) throws InterruptedException {
        return runConcurrentTest(totalPayments, 1);
    }

    public String runConcurrentTest(int totalPayments, int threads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalPayments);

        long start = System.currentTimeMillis();

        for (int i = 0; i < totalPayments; i++) {
            executor.submit(() -> {
                try {
                    PaymentRequest request = new PaymentRequest();
                    request.setAmount("100");
                    request.setCurrency("INR");
                    String response = paymentService.processPayment(request);
                    if (response.contains("SUCCESS")) success.incrementAndGet();
                    else failed.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long duration = System.currentTimeMillis() - start;
        double throughput = totalPayments / (duration / 1000.0);
        double rate = Math.round((success.get() * 100.0 / totalPayments) * 10.0) / 10.0;

        return "Threads: " + threads +
                " | Total: " + totalPayments +
                " | Success: " + success.get() +
                " | Failed: " + failed.get() +
                " | Rate: " + rate + "%" +
                " | Duration: " + duration + "ms" +
                " | Throughput: " + String.format("%.2f", throughput) + " payments/sec";
    }
}