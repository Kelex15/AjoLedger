package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.email.EmailSender;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "ajoledger.scheduler.email-outbox-cron=* * * * * *")
@Testcontainers
class EmailOutboxSchedulerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    @Autowired
    private EmailSender emailSender;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void emailIsDeliveredWhileTheSharedSchedulerThreadIsBusy() throws InterruptedException {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        taskScheduler.execute(() -> {
            busy.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            emailSender.sendDefaultNotificationEmail("member@example.com", "hello");

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(jdbcTemplate.queryForObject(
                            "SELECT status FROM email_outbox WHERE recipient = 'member@example.com'", String.class))
                            .isEqualTo("SENT"));
        } finally {
            release.countDown();
        }
    }
}
