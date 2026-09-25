package com.ajoledger.backend.funding.paypal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/funding/paypal")
@RequiredArgsConstructor
public class PayPalWebhookController {

    private final PayPalWebhookService payPalWebhookService;

    @PostMapping(path = "/webhook", version = "1")
    public ResponseEntity<Void> webhook(@RequestBody String payload) {
        payPalWebhookService.handle(payload);
        return ResponseEntity.ok().build();
    }
}
