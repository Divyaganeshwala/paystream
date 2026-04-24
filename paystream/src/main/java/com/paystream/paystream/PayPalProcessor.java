package com.paystream.paystream;


import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PayPalProcessor {

    private static final Logger log = LoggerFactory.getLogger(PayPalProcessor.class);

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    public boolean processPayment(String amount, String currency) {
        try {
            APIContext context = new APIContext(clientId, clientSecret, "sandbox");

            com.paypal.api.payments.Amount paypalAmount = new com.paypal.api.payments.Amount();
            paypalAmount.setCurrency("USD");
            paypalAmount.setTotal(amount);

            com.paypal.api.payments.Transaction transaction = new com.paypal.api.payments.Transaction();
            transaction.setAmount(paypalAmount);

            List<com.paypal.api.payments.Transaction> transactions = new ArrayList<>();
            transactions.add(transaction);

            com.paypal.api.payments.Payer payer = new com.paypal.api.payments.Payer();
            payer.setPaymentMethod("paypal");

            com.paypal.api.payments.Payment payment = new com.paypal.api.payments.Payment();
            payment.setIntent("sale");
            payment.setPayer(payer);
            payment.setTransactions(transactions);

            com.paypal.api.payments.RedirectUrls redirectUrls = new com.paypal.api.payments.RedirectUrls();
            redirectUrls.setReturnUrl("http://localhost:8080/success");
            redirectUrls.setCancelUrl("http://localhost:8080/cancel");
            payment.setRedirectUrls(redirectUrls);

            com.paypal.api.payments.Payment createdPayment = payment.create(context);
            log.info("PayPal payment created: {}", createdPayment.getId());
            return true;

        } catch (PayPalRESTException e) {
            log.error("PayPal payment failed: {}", e.getMessage());
            return false;
        }
    }
}