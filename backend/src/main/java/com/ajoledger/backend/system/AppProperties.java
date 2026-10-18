package com.ajoledger.backend.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ajoledger")
public record AppProperties(Jwt jwt, Cors cors, Security security, Google google, Frontend frontend, Scheduler scheduler,
                             Stripe stripe, PayPal paypal, Email email) {

    public record Jwt(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
    }

    public record Security(int maxFailedAttempts, int lockoutDurationMinutes, boolean secureCookies, String cookieDomain) {
    }

    public record Google(String clientId) {
    }

    public record Frontend(String baseUrl) {
    }

    public record Scheduler(String deadlineCheckCron, String enrollmentCloseCron, String reconciliationCron,
                             int reconciliationLookbackHours, String hashChainSealCron, String emailOutboxCron,
                             String emailOutboxPurgeCron) {
    }

    public record Stripe(String apiKey, String webhookSecret, int connectTimeoutMs, int readTimeoutMs,
                          int maxNetworkRetries) {
    }

    public record PayPal(String clientId, String clientSecret, String environment, int timeoutMs,
                          int uncapturedGraceMinutes) {
    }

    public record Email(String provider, String from, String resendApiKey, int timeoutMs, int maxAttempts,
                         int retentionDays) {
    }
}
