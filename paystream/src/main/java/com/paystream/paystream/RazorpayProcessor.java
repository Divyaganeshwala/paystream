package com.paystream.paystream;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RazorpayProcessor {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public boolean processPayment(String amount, String currency) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", Integer.parseInt(amount) * 100);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "receipt_" + System.currentTimeMillis());

            Order order = client.orders.create(orderRequest);
            System.out.println("Razorpay order created: " + order.get("id"));
            return true;

        } catch (Exception e) {
            System.out.println("Razorpay failed: " + e.getMessage());
            return false;
        }
    }
}