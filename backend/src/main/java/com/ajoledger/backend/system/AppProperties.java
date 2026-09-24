package com.ajoledger.backend.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ajoledger")
public record AppProperties(Jwt jwt, Cors cors, Security security, Google google, Frontend frontend, Scheduler scheduler,
                             Stripe stripe) {

    public record Jwt(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
    }

    public record Security(int maxFailedAttempts, int lockoutDurationMinutes, boolean secureCookies) {
    }

    public record Google(String clientId) {
    }

    public record Frontend(String baseUrl) {
    }

    public record Scheduler(String deadlineCheckCron, String enrollmentCloseCron, String reconciliationCron,
                             int reconciliationLookbackHours, String hashChainSealCron) {
    }

    public record Stripe(String apiKey, String webhookSecret, int connectTimeoutMs, int readTimeoutMs,
                          int maxNetworkRetries) {
    }
}
