package com.ajoledger.backend.system.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import com.ajoledger.backend.auth.JwtService;
import com.ajoledger.backend.user.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthCookieService authCookieService;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, AuthCookieService authCookieService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        authCookieService.readCookie(request, AuthCookieService.ACCESS_TOKEN_COOKIE)
                .filter(jwtService::isValid)
                .ifPresent(token -> {
                    UUID userId = jwtService.extractUserId(token);
                    userRepository.findById(userId).ifPresent(user -> {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user.getId(), null, Collections.emptyList());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                });

        filterChain.doFilter(request, response);
    }
}
