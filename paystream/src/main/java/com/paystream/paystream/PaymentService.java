package com.paystream.paystream;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class PaymentService {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RazorpayProcessor razorpayProcessor;
    private final RedisService redisService;
    private final Random random = new Random();

    public PaymentService(RouterService routerService,
                          PaymentRepository paymentRepository,
                          RazorpayProcessor razorpayProcessor,
                          RedisService redisService) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.razorpayProcessor = razorpayProcessor;
        this.redisService = redisService;
    }

    public String processPayment(PaymentRequest request) throws InterruptedException {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) {
            return "All processors are down. Payment failed.";
        }

        long start = System.currentTimeMillis();
        boolean success;
        if (processor == PaymentProcessor.RAZORPAY) {
            success = razorpayProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else {
            Thread.sleep(150 + random.nextInt(200));
            success = random.nextInt(10) != 0;
        }
        long latencyMs = System.currentTimeMillis() - start;

        String status = success ? "SUCCESS" : "FAILED";

        // Only update circuit breaker and Redis in SMART mode
        if (routerService.getCurrentMode() == RouterService.RoutingMode.SMART) {
            redisService.recordPaymentResult(processor, success, latencyMs);
            if (success) routerService.recordSuccess(processor);
            else routerService.recordFailure(processor);
        }

        paymentRepository.save(new Payment(
                request.getAmount(), request.getCurrency(), processor.name(), status
        ));

        return "Payment " + status + " on: " + processor.name()
                + " | Amount: " + request.getAmount();
    }
}
