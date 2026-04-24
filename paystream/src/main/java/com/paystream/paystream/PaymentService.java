package com.paystream.paystream;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PaymentService {

    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RazorpayProcessor razorpayProcessor;
    private final PayPalProcessor payPalProcessor;
    private final CashfreeProcessor cashfreeProcessor;
    private final RedisService redisService;
    private final RoutingLogRepository routingLogRepository;

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
        this.payPalProcessor = payPalProcessor;
        this.cashfreeProcessor = cashfreeProcessor;
        this.redisService = redisService;
        this.routingLogRepository = routingLogRepository;
    }

    public String processPayment(PaymentRequest request) throws InterruptedException {
        PaymentProcessor processor = routerService.selectProcessor();
        if (processor == null) return "All processors are down. Payment failed.";

        Map<String, Double> scoreSnapshot = routerService.getScoreSnapshot();

        long start = System.currentTimeMillis();
        boolean success;
        if (processor == PaymentProcessor.RAZORPAY) {
            success = razorpayProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else if (processor == PaymentProcessor.PAYPAL) {
            success = payPalProcessor.processPayment(request.getAmount(), request.getCurrency());
        } else {
            success = cashfreeProcessor.processPayment(request.getAmount(), request.getCurrency());
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
}