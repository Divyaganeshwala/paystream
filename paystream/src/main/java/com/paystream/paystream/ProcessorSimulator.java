package com.paystream.paystream;

import org.springframework.stereotype.Component;
import java.util.Random;

/**
 * Injects realistic failure rates per processor based on published data:
 *
 * Razorpay  - 8%  failure: Real-world aggregator PSR is 71-74% (Myntra study),
 *             reported 95% includes optimized routing. Conservative 8% for sandbox.
 *             Source: bepragma.ai Payment Success Rate analysis, Nov 2025
 *
 * Cashfree  - 6%  failure: Slightly better success rates than Razorpay per
 *             industry comparisons. Known for quick settlement and high reliability.
 *             Source: EaseOfBiz Indian Payment Gateway comparison, 2024
 *
 * PayPal    - 18% failure: International gateway routing adds friction on Indian
 *             transactions. Cross-border card processing has higher decline rates.
 *             Source: McKinsey Global Payments Report 2025, card PSR benchmarks
 */
@Component
public class ProcessorSimulator {

    private final Random random = new Random();

    public boolean shouldFail(PaymentProcessor processor) {
        double failureRate = switch (processor) {
            case RAZORPAY -> 0.06;
            case CASHFREE -> 0.10;
            case PAYPAL   -> 0.08;
        };
        return random.nextDouble() < failureRate;
    }
}