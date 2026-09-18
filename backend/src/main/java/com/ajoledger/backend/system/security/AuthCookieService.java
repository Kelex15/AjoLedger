package com.ajoledger.backend.system.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

import com.ajoledger.backend.system.AppProperties;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AppProperties appProperties;

    public AuthCookieService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ResponseCookie buildAccessTokenCookie(String token) {
        return build(ACCESS_TOKEN_COOKIE, token, appProperties.jwt().accessTokenExpiration() / 1000);
    }

    public ResponseCookie buildRefreshTokenCookie(String token) {
        return build(REFRESH_TOKEN_COOKIE, token, appProperties.jwt().refreshTokenExpiration() / 1000);
    }

    public ResponseCookie clearAccessTokenCookie() {
        return build(ACCESS_TOKEN_COOKIE, "", 0);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return build(REFRESH_TOKEN_COOKIE, "", 0);
    }

    public Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(appProperties.security().secureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
