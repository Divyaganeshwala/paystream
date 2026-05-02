package com.paystream.paystream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFallbackTest {

    @Mock private RouterService routerService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RedisService redisService;
    @Mock private RoutingLogRepository routingLogRepository;

    private PaymentGateway razorpayGateway;
    private PaymentGateway paypalGateway;
    private PaymentGateway cashfreeGateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        razorpayGateway = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.RAZORPAY; }
            public boolean processPayment(String amount, String currency) { return false; }
        };
        paypalGateway = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.PAYPAL; }
            public boolean processPayment(String amount, String currency) { return true; }
        };
        cashfreeGateway = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.CASHFREE; }
            public boolean processPayment(String amount, String currency) { return true; }
        };

        paymentService = new PaymentService(
                routerService,
                paymentRepository,
                List.of(razorpayGateway, paypalGateway, cashfreeGateway),
                redisService,
                routingLogRepository
        );
    }

    @Test
    void shouldFallbackToPaypalWhenRazorpayFails() throws InterruptedException {
        when(routerService.selectProcessor(anyList()))
                .thenReturn(PaymentProcessor.RAZORPAY)
                .thenReturn(PaymentProcessor.PAYPAL);
        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        for (PaymentProcessor p : PaymentProcessor.values()) {
            Map<String, Object> data = new HashMap<>();
            data.put("score", 100.0);
            data.put("state", "CLOSED");
            snapshot.put(p.name(), data);
        }
        when(routerService.getFullSnapshot()).thenReturn(snapshot);
        when(paymentRepository.save(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            return p;
        });

        PaymentRequest request = new PaymentRequest();
        request.setAmount("500");
        request.setCurrency("INR");

        PaymentResponse response = paymentService.processPayment(request);

        assertTrue(response.isSuccess());
        assertEquals("PAYPAL", response.getProcessor());
        assertEquals(2, response.getAttempts());
        assertTrue(response.isUsedFallback());
    }

    @Test
    void shouldFailWhenAllProcessorsFail() throws InterruptedException {
        PaymentGateway failingRazorpay = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.RAZORPAY; }
            public boolean processPayment(String amount, String currency) { return false; }
        };
        PaymentGateway failingPaypal = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.PAYPAL; }
            public boolean processPayment(String amount, String currency) { return false; }
        };
        PaymentGateway failingCashfree = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.CASHFREE; }
            public boolean processPayment(String amount, String currency) { return false; }
        };

        paymentService = new PaymentService(
                routerService,
                paymentRepository,
                List.of(failingRazorpay, failingPaypal, failingCashfree),
                redisService,
                routingLogRepository
        );

        when(routerService.selectProcessor(anyList()))
                .thenReturn(PaymentProcessor.RAZORPAY)
                .thenReturn(PaymentProcessor.PAYPAL)
                .thenReturn(PaymentProcessor.CASHFREE);
        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        for (PaymentProcessor p : PaymentProcessor.values()) {
            Map<String, Object> data = new HashMap<>();
            data.put("score", 100.0);
            data.put("state", "CLOSED");
            snapshot.put(p.name(), data);
        }
        when(routerService.getFullSnapshot()).thenReturn(snapshot);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setAmount("500");
        request.setCurrency("INR");

        PaymentResponse response = paymentService.processPayment(request);

        assertFalse(response.isSuccess());
        assertEquals("NONE", response.getProcessor());
    }

    @Test
    void shouldSucceedOnFirstAttemptWithNoFallback() throws InterruptedException {
        when(routerService.selectProcessor(anyList()))
                .thenReturn(PaymentProcessor.RAZORPAY);

        PaymentGateway successRazorpay = new PaymentGateway() {
            public PaymentProcessor getProcessor() { return PaymentProcessor.RAZORPAY; }
            public boolean processPayment(String amount, String currency) { return true; }
        };

        paymentService = new PaymentService(
                routerService,
                paymentRepository,
                List.of(successRazorpay, paypalGateway, cashfreeGateway),
                redisService,
                routingLogRepository
        );

        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        for (PaymentProcessor p : PaymentProcessor.values()) {
            Map<String, Object> data = new HashMap<>();
            data.put("score", 100.0);
            data.put("state", "CLOSED");
            snapshot.put(p.name(), data);
        }
        when(routerService.getFullSnapshot()).thenReturn(snapshot);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setAmount("500");
        request.setCurrency("INR");

        PaymentResponse response = paymentService.processPayment(request);

        assertTrue(response.isSuccess());
        assertEquals("RAZORPAY", response.getProcessor());
        assertEquals(1, response.getAttempts());
        assertFalse(response.isUsedFallback());
    }
}