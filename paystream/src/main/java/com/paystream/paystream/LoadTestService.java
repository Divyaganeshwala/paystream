package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadTestService {

    private final PaymentService paymentService;
    private final LoadTestResultRepository loadTestResultRepository;

    public LoadTestService(PaymentService paymentService,
                           LoadTestResultRepository loadTestResultRepository) {
        this.paymentService = paymentService;
        this.loadTestResultRepository = loadTestResultRepository;
    }

    public String runConcurrentTest(int totalPayments, int threads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger totalRetries = new AtomicInteger(0);
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

                    if (response.contains("Attempts: 2")) totalRetries.incrementAndGet();
                    else if (response.contains("Attempts: 3")) totalRetries.addAndGet(2);

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

        // Save result to DB
        loadTestResultRepository.save(new LoadTestResult(
                threads, totalPayments, success.get(), failed.get(),
                rate, totalRetries.get(), duration,
                Math.round(throughput * 100.0) / 100.0
        ));

        return "Threads: " + threads +
                " | Total: " + totalPayments +
                " | Success: " + success.get() +
                " | Failed: " + failed.get() +
                " | Rate: " + rate + "%" +
                " | Retries: " + totalRetries.get() +
                " | Duration: " + duration + "ms" +
                " | Throughput: " + String.format("%.2f", throughput) + " payments/sec";
    }
}