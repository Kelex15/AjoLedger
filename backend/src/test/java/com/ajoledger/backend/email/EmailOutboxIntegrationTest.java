package com.ajoledger.backend.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// The scheduled run is switched off so it can't deliver rows out from under the test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "ajoledger.scheduler.email-outbox-cron=-")
@Testcontainers
class EmailOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private EmailSender emailSender;
    @Autowired
    private EmailDeliveryService emailDeliveryService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailTransport emailTransport;

    @BeforeEach
    void clearOutbox() {
        jdbcTemplate.update("DELETE FROM email_outbox");
    }

    private Map<String, Object> onlyRow() {
        return jdbcTemplate.queryForMap("SELECT * FROM email_outbox");
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM email_outbox", Integer.class);
    }

    @Test
    void emailIsQueuedOnlyWhenTheCallersTransactionCommits() {
        transactionTemplate.executeWithoutResult(status -> {
            emailSender.sendDefaultNotificationEmail("rolled-back@example.com", "never sent");
            status.setRollbackOnly();
        });
        assertThat(rowCount()).isZero();

        transactionTemplate.executeWithoutResult(status ->
                emailSender.sendDefaultNotificationEmail("committed@example.com", "sent"));
        assertThat(onlyRow()).containsEntry("recipient", "committed@example.com").containsEntry("status", "PENDING");
    }

    @Test
    void successfulSendMarksRowSent() {
        emailSender.sendDefaultNotificationEmail("member@example.com", "hello");

        assertThat(emailDeliveryService.deliverDue()).isEqualTo(1);

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 1);
        assertThat(row.get("sent_at")).isNotNull();
    }

    @Test
    void retryableFailureSchedulesBackoffAndIsNotRetriedEarly() {
        doThrow(new EmailDeliveryException("503", true)).when(emailTransport).send(any());
        emailSender.sendDefaultNotificationEmail("member@example.com", "hello");

        emailDeliveryService.deliverDue();

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1).containsEntry("last_error", "503");
        Instant nextAttemptAt = ((Timestamp) row.get("next_attempt_at")).toInstant();
        assertThat(nextAttemptAt).isAfter(Instant.now().plus(Duration.ofSeconds(25)));

        emailDeliveryService.deliverDue();
        assertThat(onlyRow()).containsEntry("attempts", 1);
    }

    @Test
    void permanentFailureMarksRowFailedImmediately() {
        doThrow(new EmailDeliveryException("422", false)).when(emailTransport).send(any());
        emailSender.sendDefaultNotificationEmail("member@example.com", "hello");

        emailDeliveryService.deliverDue();

        assertThat(onlyRow()).containsEntry("status", "FAILED").containsEntry("attempts", 1);
    }

    @Test
    void unexpectedExceptionIsRetriedRatherThanLoopingOrDropped() {
        doThrow(new IllegalStateException("bug")).when(emailTransport).send(any());
        emailSender.sendDefaultNotificationEmail("member@example.com", "hello");

        emailDeliveryService.deliverDue();

        assertThat(onlyRow()).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    }

    @Test
    void lastAllowedAttemptFailingMarksRowFailed() {
        doThrow(new EmailDeliveryException("503", true)).when(emailTransport).send(any());
        emailSender.sendDefaultNotificationEmail("member@example.com", "hello");
        jdbcTemplate.update("UPDATE email_outbox SET attempts = 7");

        emailDeliveryService.deliverDue();

        assertThat(onlyRow()).containsEntry("status", "FAILED").containsEntry("attempts", 8);
    }

    @Test
    void expiredEmailIsFailedWithoutBeingSent() {
        emailSender.sendVerificationEmail("new@example.com", "http://localhost/verify-email?token=x",
                Instant.now().minus(Duration.ofMinutes(1)));

        emailDeliveryService.deliverDue();

        assertThat(onlyRow()).containsEntry("status", "FAILED");
        verify(emailTransport, never()).send(any());
    }

    @Test
    void purgeRemovesOldFinishedRowsButKeepsPendingOnes() {
        emailSender.sendDefaultNotificationEmail("sent@example.com", "hello");
        emailDeliveryService.deliverDue();
        emailSender.sendDefaultNotificationEmail("pending@example.com", "hello");
        jdbcTemplate.update("UPDATE email_outbox SET created_at = now() - interval '31 days'");

        int purged = emailDeliveryService.purgeFinishedBefore(Instant.now().minus(Duration.ofDays(30)));

        assertThat(purged).isEqualTo(1);
        assertThat(onlyRow()).containsEntry("recipient", "pending@example.com");
    }
}
