package com.paystream.paystream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CashfreeProcessor implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(CashfreeProcessor.class);

    @Value("${cashfree.client.id}")
    private String clientId;

    @Value("${cashfree.client.secret}")
    private String clientSecret;

    private final ProcessorSimulator simulator;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String SANDBOX_URL = "https://sandbox.cashfree.com/pg/orders";

    public CashfreeProcessor(ProcessorSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public PaymentProcessor getProcessor() { return PaymentProcessor.CASHFREE; }

    @Override
    public boolean processPayment(String amount, String currency) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-client-secret", clientSecret);
            headers.set("x-api-version", "2022-09-01");
            headers.set("Accept", "application/json");

            Map<String, Object> customerDetails = new HashMap<>();
            customerDetails.put("customer_id", "CUST_" + UUID.randomUUID().toString().substring(0, 8));
            customerDetails.put("customer_name", "Test User");
            customerDetails.put("customer_email", "test@paystream.com");
            customerDetails.put("customer_phone", "+919876543210");

            Map<String, Object> orderMeta = new HashMap<>();
            orderMeta.put("return_url", "http://localhost:8080/success");

            Map<String, Object> body = new HashMap<>();
            body.put("order_amount", amount);
            body.put("order_currency", "INR");
            body.put("customer_details", customerDetails);
            body.put("order_meta", orderMeta);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(SANDBOX_URL, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map responseBody = response.getBody();
                log.info("Cashfree order created: {}", responseBody.get("order_id"));

                // API succeeded — now apply realistic failure simulation
                if (simulator.shouldFail(PaymentProcessor.CASHFREE)) {
                    log.warn("Cashfree simulated decline (6% rate — high reliability gateway)");
                    return false;
                }
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("Cashfree payment failed: {}", e.getMessage());
            return false;
        }
    }
}