package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Random;

@Service
public class PaymentService {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RazorpayProcessor razorpayProcessor;
    private final PayPalProcessor payPalProcessor;
    private final CashfreeProcessor cashfreeProcessor;
    private final RedisService redisService;
    private final RoutingLogRepository routingLogRepository;
    private final Random random = new Random();

    public PaymentService(RouterService routerService,
                          PaymentRepository paymentRepository,
                          RazorpayProcessor razorpayProcessor,
                          PayPalProcessor payPalProcessor,
                          CashfreeProcessor cashfreeProcessor,
                          RedisService redisService,
                          RoutingLogRepository routingLogRepository) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.razorpayProcessor = razorpayProcessor;
        this.redisService = redisService;
        this.routingLogRepository = routingLogRepository;
        this.payPalProcessor= payPalProcessor;
        this.cashfreeProcessor= cashfreeProcessor;

    }

    public String processPayment(PaymentRequest request) throws InterruptedException {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) return "All processors are down. Payment failed.";

        Map<String, Double> scoreSnapshot = routerService.getScoreSnapshot();

        long start = System.currentTimeMillis();
        boolean success;
        if (processor == PaymentProcessor.RAZORPAY) {
            success = razorpayProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else if (processor == PaymentProcessor.PAYU) {
            success = payPalProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else if (processor == PaymentProcessor.CASHFREE) {
            success = cashfreeProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else {
            Thread.sleep(150 + random.nextInt(200));
            success = random.nextInt(10) != 0;
        }
        long latencyMs = System.currentTimeMillis() - start;

        redisService.recordPaymentResult(processor, success, latencyMs);
        if (success) routerService.recordSuccess(processor);
        else routerService.recordFailure(processor);

        Payment savedPayment = paymentRepository.save(
                new Payment(request.getAmount(), request.getCurrency(),
                        processor.name(), success ? "SUCCESS" : "FAILED")
        );

        for (PaymentProcessor p : PaymentProcessor.values()) {
            ProcessorHealth health = routerService.getHealthMap().get(p);
            routingLogRepository.save(new RoutingLog(
                    savedPayment.getId(), p.name(),
                    scoreSnapshot.get(p.name()),
                    health.getState().name(),
                    p == processor
            ));
        }

        return "Payment " + (success ? "SUCCESS" : "FAILED") + " on: " + processor.name()
                + " | Amount: " + request.getAmount();
    }

    public String processSingleProcessorPayment(PaymentRequest request) throws InterruptedException {
        long start = System.currentTimeMillis();
        boolean success = razorpayProcessor.processPayment(request.getAmount(), request.getCurrency());
        long latencyMs = System.currentTimeMillis() - start;
        return "Payment " + (success ? "SUCCESS" : "FAILED") +
                " on: RAZORPAY | Latency: " + latencyMs + "ms";
    }
}