package com.ajoledger.backend.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.auth.JwtService;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class DemoAccessIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RestTestClient restTestClient;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private JwtService jwtService;

    private String accessTokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .username(username)
                    .firstName("Test")
                    .lastName("User")
                    .email(username + "@ajoledger.invalid")
                    .build());
            accountService.provisionDefaultAccounts(created);
            return created;
        });
        return jwtService.generateAccessToken(user);
    }

    private String demoToken() {
        return accessTokenFor(Constants.Demo.USERNAME);
    }

    private String csrfToken() {
        return restTestClient.get().uri("/api/ledger/lien").exchange().returnResult()
                .getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private RestTestClient.ResponseSpec post(String uri, String accessToken) {
        String csrf = csrfToken();
        RestTestClient.RequestBodySpec request = restTestClient.post().uri(uri)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf);
        if (accessToken != null) {
            request.cookie("access_token", accessToken);
        }
        return request.body(Map.of()).exchange();
    }

    @Test
    void demoSessionCanRead() {
        restTestClient.get().uri("/api/accounts/me")
                .cookie("access_token", demoToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void demoSessionCannotWrite() {
        String token = demoToken();
        post("/api/ledger/transfer", token).expectStatus().isForbidden();
        post("/api/ledger/lien/hold", token).expectStatus().isForbidden();
        post("/api/circles", token).expectStatus().isForbidden();
        post("/api/funding/deposit", token).expectStatus().isForbidden();
    }

    @Test
    void demoSessionCanLogOut() {
        post("/api/auth/logout", demoToken()).expectStatus().isNoContent();
    }

    @Test
    void regularUserWritesStillReachTheEndpoint() {
        String token = accessTokenFor("user" + UUID.randomUUID().toString().substring(0, 8));
        // An empty transfer fails validation, which proves the request got past authorization.
        post("/api/ledger/transfer", token).expectStatus().isBadRequest();
    }

    @Test
    void anonymousWriteIsStillUnauthorizedRatherThanForbidden() {
        post("/api/ledger/transfer", null).expectStatus().isUnauthorized();
    }
}
