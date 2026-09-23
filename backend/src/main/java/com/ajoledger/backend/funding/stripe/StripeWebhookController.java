package com.ajoledger.backend.funding.stripe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/funding/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping(path = "/webhook", version = "1")
    public ResponseEntity<Void> webhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature)
            throws com.stripe.exception.SignatureVerificationException {
        stripeWebhookService.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
