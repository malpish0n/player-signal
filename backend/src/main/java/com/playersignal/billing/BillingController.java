package com.playersignal.billing;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class BillingController {
    public record Checkout(String plan) {}
    private final BillingService service;private final BillingWebhook webhook;
    public BillingController(BillingService service,BillingWebhook webhook){this.service=service;this.webhook=webhook;}
    @GetMapping("/api/workspace/billing") public BillingService.Status status(){return service.status();}
    @PostMapping("/api/workspace/billing/checkout")public BillingService.Link checkout(@RequestBody Checkout input){if(input.plan()==null)throw new com.playersignal.shared.ApiException(400,"INVALID_PLAN","Choose a plan.");return service.checkout(input.plan());}
    @PostMapping("/api/workspace/billing/portal")public BillingService.Link portal(){return service.portal();}
    @PostMapping("/api/workspace/billing/refresh")public BillingService.Status refresh(){return service.refresh();}
    @PostMapping("/api/billing/webhook")public Map<String,Boolean> webhook(HttpServletRequest request)throws IOException{
        byte[] bytes=request.getInputStream().readNBytes(262145);if(bytes.length>262144)throw new com.playersignal.shared.ApiException(413,"EVENT_TOO_LARGE","Webhook body exceeds the limit.");webhook.receive(bytes,request.getHeader("Stripe-Signature"));return Map.of("received",true);
    }
}
