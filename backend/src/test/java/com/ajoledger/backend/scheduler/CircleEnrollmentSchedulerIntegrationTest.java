package com.ajoledger.backend.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleRepository;
import com.ajoledger.backend.circle.CircleService;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CircleEnrollmentSchedulerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CircleEnrollmentScheduler scheduler;
    @Autowired
    private CircleService circleService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private FundingService fundingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User createFundedUser(BigDecimal savingsBalance) {
        User user = userRepository.save(User.builder()
                .username("user" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("User")
                .email("test-" + UUID.randomUUID() + "@example.com")
                .build());
        accountService.createUserAccount(user, UserAccountType.SAVINGS);
        accountService.createUserAccount(user, UserAccountType.LIEN);
        fundingService.postDeposit(user.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                savingsBalance, UUID.randomUUID().toString());
        return user;
    }

    /** Two members, the platform minimum, join before the deadline gets pushed into the past,
     * so closing enrollment activates the circle without also immediately completing it. */
    private Circle createPastDeadlineOpenEnrollmentCircle() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Test Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.OPEN_ENROLLMENT)
                .maxMembers(5)
                .minMembers(2)
                .enrollmentDeadline(Instant.now().plus(Duration.ofDays(30)))
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(false)
                .build();

        Circle circle = circleService.createCircle(creator.getId(), dto);
        circleService.joinCircle(member2.getId(), circle.getId());
        // enrollmentDeadline is updatable=false at the entity level. A JPA save() here would
        // silently no-op, so push it into the past via raw SQL instead.
        jdbcTemplate.update("UPDATE circle SET enrollment_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), circle.getId());
        return circle;
    }

    @Test
    void runEnrollmentChecksProcessesRealPastDeadlineCircles() {
        Circle circle = createPastDeadlineOpenEnrollmentCircle();

        scheduler.runEnrollmentChecks();

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }

    @Test
    void oneCircleFailingDoesNotBlockOthersFromProcessing() {
        Circle circle = createPastDeadlineOpenEnrollmentCircle();
        UUID nonExistentCircleId = UUID.randomUUID();

        // nonExistentCircleId fails immediately (Circle not found) - it comes first in the list
        // specifically to prove that failure doesn't stop the real circle after it from being tried
        scheduler.processCircles(List.of(nonExistentCircleId, circle.getId()));

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }
}
