package com.paystream.paystream;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final RouterService routerService;

    public PaymentController(RouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping("/payment")
    public String processPayment(@RequestBody String body) {

        PaymentProcessor processor = routerService.selectProcessor();
        return "Payment routed to: " + processor.name() + " | Request: " + body;
    }
}