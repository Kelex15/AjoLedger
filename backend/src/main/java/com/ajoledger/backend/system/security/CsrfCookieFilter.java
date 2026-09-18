package com.ajoledger.backend.system.security;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;

/** Deferred CSRF token loading only renders the token into its cookie once something reads
 * it - without this, the cookie never gets set on the response that's supposed to carry it.
 * Not a @Component: Spring Boot auto-registers any Filter bean as a generic servlet filter
 * on top of wherever HttpSecurity adds it, which runs this twice per request and, since it
 * mutates the deferred CSRF token, alternately wipes the cookie it's supposed to maintain. */
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
