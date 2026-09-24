package com.ajoledger.backend.funding.stripe;

import com.stripe.StripeClient;

import com.ajoledger.backend.system.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(AppProperties appProperties) {
        AppProperties.Stripe stripe = appProperties.stripe();
        return StripeClient.builder()
                .setApiKey(stripe.apiKey())
                .setConnectTimeout(stripe.connectTimeoutMs())
                .setReadTimeout(stripe.readTimeoutMs())
                .setMaxNetworkRetries(stripe.maxNetworkRetries())
                .build();
    }
}
