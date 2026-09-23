package com.ajoledger.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ajoledger.backend.auth.audit.AuditEventType;
import com.ajoledger.backend.auth.audit.AuditLogRepository;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import org.springframework.test.web.servlet.client.ExchangeResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "This-is-a-test-password-lol";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RestTestClient restTestClient;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAuthRepository userAuthRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private MessageResponseDTO signupExpecting(String email, org.springframework.http.HttpStatus expectedStatus) {
        Map<String, String> body = Map.of(
                "username", "user" + UUID.randomUUID().toString().substring(0, 8),
                "firstName", "Test",
                "lastName", "User",
                "email", email,
                "password", PASSWORD
        );
        return restTestClient.post().uri("/api/auth/signup")
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus.value())
                .expectBody(MessageResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    private String verificationTokenFor(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        UserAuth userAuth = userAuthRepository.findByUserId(user.getId()).orElseThrow();
        return userAuth.getVerificationToken();
    }

    private void verifyEmail(String token) {
        restTestClient.post().uri("/api/auth/verify-email")
                .body(Map.of("token", token))
                .exchange()
                .expectStatus().isOk();
    }

    private String cookieValue(ExchangeResult result, String name) {
        return result.getResponseCookies().getFirst(name).getValue();
    }

    /** POST endpoints outside the pre-auth flow (login/signup/etc.) require a matching
     * XSRF-TOKEN cookie and X-XSRF-TOKEN header. Grab one from any GET response. */
    private String csrfToken() {
        ExchangeResult result = restTestClient.get().uri("/api/ledger/lien").exchange().returnResult();
        return cookieValue(result, "XSRF-TOKEN");
    }

    @Test
    void fullHappyPathFlow() {
        String email = uniqueEmail();

        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);
        verifyEmail(verificationTokenFor(email));

        ExchangeResult loginResult = restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        String accessToken = cookieValue(loginResult, "access_token");
        String refreshToken = cookieValue(loginResult, "refresh_token");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        User user = userRepository.findByEmail(email).orElseThrow();

        restTestClient.get().uri("/api/user/" + user.getId())
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get().uri("/api/user/" + user.getId())
                .cookie("access_token", accessToken)
                .exchange()
                .expectStatus().isOk();

        String csrf = csrfToken();

        ExchangeResult rotateResult = restTestClient.post().uri("/api/auth/refresh")
                .cookie("refresh_token", refreshToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        String rotatedAccessToken = cookieValue(rotateResult, "access_token");
        String rotatedRefreshToken = cookieValue(rotateResult, "refresh_token");
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        restTestClient.post().uri("/api/auth/logout")
                .cookie("access_token", rotatedAccessToken)
                .cookie("refresh_token", rotatedRefreshToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.post().uri("/api/auth/refresh")
                .cookie("refresh_token", rotatedRefreshToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshTokenReuseRevokesWholeFamily() {
        String email = uniqueEmail();
        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);
        verifyEmail(verificationTokenFor(email));

        ExchangeResult loginResult = restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        String originalRefreshToken = cookieValue(loginResult, "refresh_token");

        String csrf = csrfToken();

        ExchangeResult rotateResult = restTestClient.post().uri("/api/auth/refresh")
                .cookie("refresh_token", originalRefreshToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        String activeToken = cookieValue(rotateResult, "refresh_token");

        // reusing the already-rotated (revoked) original token triggers reuse detection
        restTestClient.post().uri("/api/auth/refresh")
                .cookie("refresh_token", originalRefreshToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isUnauthorized();

        // the token that was still valid a moment ago must now also be dead, whole family revoked
        restTestClient.post().uri("/api/auth/refresh")
                .cookie("refresh_token", activeToken)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isUnauthorized();

        User user = userRepository.findByEmail(email).orElseThrow();
        boolean reuseLogged = auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getEventType() == AuditEventType.REFRESH_TOKEN_REUSE_DETECTED
                        && log.getUser() != null && log.getUser().getId().equals(user.getId()));
        assertThat(reuseLogged).isTrue();
    }

    @Test
    void loginLocksAccountAfterTooManyFailedAttempts() {
        String email = uniqueEmail();
        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);
        verifyEmail(verificationTokenFor(email));

        // the 5th wrong attempt itself crosses the threshold and locks the account immediately
        for (int i = 0; i < 4; i++) {
            restTestClient.post().uri("/api/auth/login")
                    .body(Map.of("email", email, "password", "wrong-password"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", "wrong-password"))
                .exchange()
                .expectStatus().isEqualTo(423);

        restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(423);

        // simulate the lockout window having passed
        User user = userRepository.findByEmail(email).orElseThrow();
        UserAuth userAuth = userAuthRepository.findByUserId(user.getId()).orElseThrow();
        userAuth.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        userAuthRepository.save(userAuth);

        restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void loginRejectedWhenEmailNotVerified() {
        String email = uniqueEmail();
        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);

        restTestClient.post().uri("/api/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void signupReclaimsStaleUnverifiedAccount() {
        String email = uniqueEmail();
        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);
        User firstAttemptUser = userRepository.findByEmail(email).orElseThrow();

        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);

        User secondAttemptUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(secondAttemptUser.getId()).isNotEqualTo(firstAttemptUser.getId());
        assertThat(userRepository.findById(firstAttemptUser.getId())).isEmpty();
    }

    @Test
    void signupRejectedWhenEmailAlreadyVerified() {
        String email = uniqueEmail();
        signupExpecting(email, org.springframework.http.HttpStatus.CREATED);
        verifyEmail(verificationTokenFor(email));

        signupExpecting(email, org.springframework.http.HttpStatus.CONFLICT);
    }
}
