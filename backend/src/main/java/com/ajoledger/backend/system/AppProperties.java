package com.ajoledger.backend.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ajoledger")
public record AppProperties(Jwt jwt, Cors cors) {

    public record Jwt(String secret, long expiration) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
    }
}
