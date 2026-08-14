package com.ajoledger.backend.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ajoledger")
public record AppProperties(Jwt jwt, Cors cors, Security security, Google google, Frontend frontend) {

    public record Jwt(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
    }

    public record Security(int maxFailedAttempts, int lockoutDurationMinutes) {
    }

    public record Google(String clientId) {
    }

    public record Frontend(String baseUrl) {
    }
}
