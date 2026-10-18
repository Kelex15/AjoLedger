package com.ajoledger.backend.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailTransportTest {

    private static final OutboxEmail EMAIL = new OutboxEmail(
            UUID.fromString("7d1f6a52-3c1e-4b8e-9a64-2f0d8c5b1e90"), "member@example.com", "Subject", "Body", 0, null);

    private MockRestServiceServer server;
    private ResendEmailTransport transport;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        transport = new ResendEmailTransport(builder, "re_test_key", "AjoLedger <noreply@ajoledger.xyz>");
    }

    @Test
    void sendsTheRowIdAsTheIdempotencyKey() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", EMAIL.id().toString()))
                .andExpect(content().json("""
                        {"from": "AjoLedger <noreply@ajoledger.xyz>", "to": ["member@example.com"],
                         "subject": "Subject", "text": "Body"}
                        """))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        transport.send(EMAIL);

        server.verify();
    }

    @Test
    void malformedRequestIsPermanent() {
        assertThat(failureFor(HttpStatus.UNPROCESSABLE_CONTENT).isRetryable()).isFalse();
        server.reset();
        assertThat(failureFor(HttpStatus.BAD_REQUEST).isRetryable()).isFalse();
    }

    @Test
    void outagesRateLimitsAndConfigurationErrorsAreRetryable() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.CONFLICT}) {
            server.reset();
            assertThat(failureFor(status).isRetryable()).as(status.toString()).isTrue();
        }
    }

    @Test
    void timeoutIsRetryable() {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withException(new SocketTimeoutException()));

        assertThatThrownBy(() -> transport.send(EMAIL))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    private EmailDeliveryException failureFor(HttpStatus status) {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(status));
        try {
            transport.send(EMAIL);
        } catch (EmailDeliveryException e) {
            return e;
        }
        throw new AssertionError("expected " + status + " to fail delivery");
    }
}
