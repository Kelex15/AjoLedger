package com.ajoledger.backend.funding.stripe;

import com.stripe.StripeClient;

import com.ajoledger.backend.system.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(AppProperties appProperties) {
        return new StripeClient(appProperties.stripe().apiKey());
    }
}
