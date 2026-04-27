package com.paystream.paystream;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        List<PaymentProcessor> tried = new ArrayList<>();

        for (int attempt = 0; attempt < PaymentProcessor.values().length; attempt++) {
            PaymentProcessor processor = routerService.selectProcessor(tried);
            if (processor == null) break;
            tried.add(processor);

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

            if (success) {
                Payment savedPayment = paymentRepository.save(
                        new Payment(request.getAmount(), request.getCurrency(),
                                processor.name(), "SUCCESS")
                );
                saveRoutingLog(savedPayment.getId(), scoreSnapshot, processor);
                return "Payment SUCCESS on: " + processor.name()
                        + " | Amount: " + request.getAmount()
                        + " | Attempts: " + (attempt + 1);
            }

            // failed — wait before retry (exponential backoff)
            if (attempt < PaymentProcessor.values().length - 1) {
                Thread.sleep(100 * (long) Math.pow(2, attempt));
            }
        }

        // all processors failed
        Payment savedPayment = paymentRepository.save(
                new Payment(request.getAmount(), request.getCurrency(), "NONE", "FAILED")
        );
        return "Payment FAILED on all processors | Amount: " + request.getAmount();
    }

    private void saveRoutingLog(Long paymentId, Map<String, Double> scoreSnapshot,
                                PaymentProcessor selected) {
        for (PaymentProcessor p : PaymentProcessor.values()) {
            ProcessorHealth health = routerService.getHealthMap().get(p);
            routingLogRepository.save(new RoutingLog(
                    paymentId, p.name(),
                    scoreSnapshot.get(p.name()),
                    health.getState().name(),
                    p == selected
            ));
        }
    }
}