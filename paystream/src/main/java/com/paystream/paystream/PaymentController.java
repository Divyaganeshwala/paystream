package com.paystream.paystream;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @PostMapping("/payment")
    public String processPayment(@RequestBody String body) {
        return "Payment received: " + body;
    }
}
