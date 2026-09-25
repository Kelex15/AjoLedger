package com.ajoledger.backend.funding.paypal;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;

import com.ajoledger.backend.system.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayPalConfig {

    @Bean
    public PaypalServerSdkClient paypalClient(AppProperties appProperties) {
        AppProperties.PayPal paypal = appProperties.paypal();
        return new PaypalServerSdkClient.Builder()
                .environment(Environment.valueOf(paypal.environment()))
                .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(
                        paypal.clientId(), paypal.clientSecret()).build())
                .httpClientConfig(config -> config.timeout(paypal.timeoutMs()))
                .build();
    }
}
