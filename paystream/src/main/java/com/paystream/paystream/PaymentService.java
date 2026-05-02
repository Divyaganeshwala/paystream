package com.paystream.paystream;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final Map<PaymentProcessor, PaymentGateway> gateways;
    private final RouterService routerService;
    private final PaymentRepository paymentRepository;
    private final RedisService redisService;
    private final RoutingLogRepository routingLogRepository;

    public PaymentService(RouterService routerService,
                          PaymentRepository paymentRepository,
                          List<PaymentGateway> gatewayList,
                          RedisService redisService,
                          RoutingLogRepository routingLogRepository) {
        this.routerService = routerService;
        this.paymentRepository = paymentRepository;
        this.redisService = redisService;
        this.routingLogRepository = routingLogRepository;
        this.gateways = new HashMap<>();
        for (PaymentGateway gateway : gatewayList) {
            this.gateways.put(gateway.getProcessor(), gateway);
        }
    }

    public PaymentResponse processPayment(PaymentRequest request) throws InterruptedException {
        List<PaymentProcessor> tried = new ArrayList<>();
        List<PaymentProcessor> attempted = new ArrayList<>();
        boolean usedFallback = false;

        // Capture initiation time before any attempt
        LocalDateTime initiatedAt = LocalDateTime.now(ZoneId.of("UTC"));

        // Capture full snapshot BEFORE making any payment call
        Map<String, Map<String, Object>> snapshot = routerService.getFullSnapshot();

        for (int attempt = 0; attempt < PaymentProcessor.values().length; attempt++) {
            PaymentProcessor processor = routerService.selectProcessor(tried);
            if (processor == null) break;
            tried.add(processor);
            attempted.add(processor);

            long start = System.currentTimeMillis();
            boolean success;

            success = gateways.get(processor).processPayment(request.getAmount(), request.getCurrency());
            long latencyMs = System.currentTimeMillis() - start;

            redisService.recordPaymentResult(processor, success, latencyMs);
            if (success) routerService.recordSuccess(processor);
            else {
                routerService.recordFailure(processor);
                if (attempt > 0) usedFallback = true;
            }

            if (success) {
                Payment savedPayment = paymentRepository.save(
                        new Payment(request.getAmount(), request.getCurrency(),
                                processor.name(), "SUCCESS", attempt > 0, initiatedAt)
                );
                saveRoutingLog(savedPayment.getId(), snapshot, processor, attempted);
                return new PaymentResponse(true, processor.name(), request.getAmount(), attempt + 1, "SUCCESS");
            }
        }

        Payment savedPayment = paymentRepository.save(
                new Payment(request.getAmount(), request.getCurrency(),
                        "NONE", "FAILED", usedFallback, initiatedAt)
        );
        return new PaymentResponse(false, "NONE", request.getAmount(), tried.size(), "FAILED");
    }

    private void saveRoutingLog(Long paymentId, Map<String, Map<String, Object>> snapshot,
                                PaymentProcessor selected, List<PaymentProcessor> attempted) {
        for (PaymentProcessor p : PaymentProcessor.values()) {
            Map<String, Object> data = snapshot.get(p.name());
            double score = (double) data.get("score");
            String state = (String) data.get("state");
            boolean wasSelected = p == selected;
            boolean wasAttempted = attempted.contains(p);
            routingLogRepository.save(new RoutingLog(
                    paymentId, p.name(), score, state, wasSelected, wasAttempted
            ));
        }
    }
}