package com.ajoledger.backend.auth;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.ajoledger.backend.system.AppProperties;
import com.ajoledger.backend.user.User;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenExpirationMillis;

    public JwtService(AppProperties appProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(appProperties.jwt().secret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMillis = appProperties.jwt().accessTokenExpiration();
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + accessTokenExpirationMillis);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMillis / 1000;
    }

    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
