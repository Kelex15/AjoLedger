package com.ajoledger.backend.system.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.auth.JwtService;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.UserRepository;

/** Not a @Component: Spring Boot auto-registers any Filter bean as a generic servlet
 * filter on top of wherever HttpSecurity adds it, running this twice per request. */
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
                        List<SimpleGrantedAuthority> authorities = Constants.Demo.USERNAME.equals(user.getUsername())
                                ? List.of(new SimpleGrantedAuthority(SecurityConfig.DEMO_AUTHORITY))
                                : List.of();
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                });

        filterChain.doFilter(request, response);
    }
}
