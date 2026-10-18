package com.ajoledger.backend.email.transport;

import com.ajoledger.backend.email.OutboxEmail;
import com.ajoledger.backend.system.AppProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "ajoledger.email", name = "provider", havingValue = "resend")
public class ResendEmailTransport implements EmailTransport {

    private static final String RESEND_BASE_URL = "https://api.resend.com";

    private final RestClient restClient;
    private final String from;

    @Autowired
    public ResendEmailTransport(AppProperties appProperties) {
        this(RestClient.builder().requestFactory(requestFactory(appProperties.email().timeoutMs())),
                appProperties.email().resendApiKey(), appProperties.email().from());
    }

    ResendEmailTransport(RestClient.Builder builder, String apiKey, String from) {
        this.restClient = builder
                .baseUrl(RESEND_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    private static SimpleClientHttpRequestFactory requestFactory(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }

    @Override
    public void send(OutboxEmail email) {
        try {
            restClient.post()
                    .uri("/emails")
                    // Resend drops a resend of the same row if a crash kept it from being marked sent.
                    .header("Idempotency-Key", email.id().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendEmailRequest(from, List.of(email.recipient()), email.subject(), email.body()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new EmailDeliveryException("Resend returned " + status + ": " + e.getResponseBodyAsString(),
                    isRetryable(status));
        } catch (ResourceAccessException e) {
            throw new EmailDeliveryException("Could not reach Resend: " + e.getMessage(), true);
        }
    }

    /** Only 400 and 422 say this particular email can never be sent. A 401 or 403 is a key or
     * domain problem that fixing the deployment resolves, and the email should still go out after. */
    private static boolean isRetryable(int status) {
        return status != 400 && status != 422;
    }

    private record SendEmailRequest(String from, List<String> to, String subject, String text) {
    }
}
