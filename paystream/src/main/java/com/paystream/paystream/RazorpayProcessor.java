package com.paystream.paystream;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RazorpayProcessor {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RazorpayProcessor.class);

    public boolean processPayment(String amount, String currency) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", Integer.parseInt(amount) * 100);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "receipt_" + System.currentTimeMillis());

            Order order = client.orders.create(orderRequest);
            log.info("Razorpay order created: {}", Optional.ofNullable(order.get("id")));
            return true;

        } catch (Exception e) {
            log.error("Razorpay payment failed: {}", e.getMessage());
            return false;
        }
    }
}