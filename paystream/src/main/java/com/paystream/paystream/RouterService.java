package com.paystream.paystream;
import org.springframework.stereotype.Service;
import java.util.Random;
@Service
public class RouterService {

    private final Random random = new Random();

    public PaymentProcessor selectProcessor() {
        PaymentProcessor[] processors = PaymentProcessor.values();
        return processors[random.nextInt(processors.length)];
    }
}
