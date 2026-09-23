package com.ajoledger.backend.system.security;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;

/** The CSRF token Spring Security attaches to the request is lazy: nothing writes it to the
 * response cookie until something actually calls getToken() on it. This filter exists just to
 * force that read.
 * Deliberately not a @Component: Spring Boot would then also register it as a plain servlet
 * filter on top of the one HttpSecurity already wires in, so it runs twice per request. Reading
 * the same deferred token twice toggles it, wiping the cookie every other request. */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
