package com.ajoledger.backend.system.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import com.ajoledger.backend.auth.JwtService;
import com.ajoledger.backend.system.AppProperties;
import com.ajoledger.backend.user.UserRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String DEMO_AUTHORITY = "DEMO";

    private static final AccessDeniedHandlerImpl DEFAULT_ACCESS_DENIED_HANDLER = new AccessDeniedHandlerImpl();

    /** Refresh is already public; logout is the only authenticated write a demo session needs. */
    private static final String[] DEMO_ALLOWED_WRITE_ENDPOINTS = {
            "/api/auth/logout"
    };

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/demo",
            "/api/auth/google",
            "/api/auth/refresh"
    };

    private static final String[] PUBLIC_WEBHOOK_ENDPOINTS = {
            "/api/funding/stripe/webhook",
            "/api/funding/paypal/webhook"
    };

    private static final String[] ERROR_DISPATCH_ENDPOINTS = {
            "/error"
    };

    private static final String[] PUBLIC_HEALTH_ENDPOINTS = {
            "/actuator/health"
    };

    private static final String[] CSRF_EXEMPT_ENDPOINTS = {
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/demo",
            "/api/auth/google",
            "/api/funding/stripe/webhook",
            "/api/funding/paypal/webhook"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserRepository userRepository,
                                                     AuthCookieService authCookieService, AppProperties appProperties) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository(appProperties))
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(CSRF_EXEMPT_ENDPOINTS)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(SecurityConfig::handleAccessDenied))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_WEBHOOK_ENDPOINTS).permitAll()
                        .requestMatchers(ERROR_DISPATCH_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_HEALTH_ENDPOINTS).permitAll()
                        // A demo session can read everything and change nothing. Relies on GETs never mutating.
                        .requestMatchers(HttpMethod.GET, "/**").authenticated()
                        .requestMatchers(DEMO_ALLOWED_WRITE_ENDPOINTS).authenticated()
                        .anyRequest().access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                AuthorizationManagers.not(AuthorityAuthorizationManager.hasAuthority(DEMO_AUTHORITY))))
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, authCookieService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** The read-only rule is the only authorization rule that denies, so outside of CSRF failures, a denial
     * means a demo session tried to change something. Saying so lets the app show it next to the control. */
    private static void handleAccessDenied(HttpServletRequest request, HttpServletResponse response,
                                           AccessDeniedException exception) throws IOException, ServletException {
        if (exception instanceof CsrfException) {
            DEFAULT_ACCESS_DENIED_HANDLER.handle(request, response, exception);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"The demo account can look around but can't make changes. Create an account to try this.\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Unlike the httpOnly auth cookies, this one also needs to be readable by frontend JS
     * running on a different subdomain, so it needs a broader Domain than the default. */
    private CookieCsrfTokenRepository csrfTokenRepository(AppProperties appProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        String cookieDomain = appProperties.security().cookieDomain();
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            repository.setCookieCustomizer(builder -> builder.domain(cookieDomain));
        }
        return repository;
    }
}
