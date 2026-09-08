package com.ajoledger.backend.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleMembershipRepository;
import com.ajoledger.backend.circle.CircleRepository;
import com.ajoledger.backend.circle.CircleService;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CircleRoundSchedulerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CircleRoundScheduler scheduler;
    @Autowired
    private CircleService circleService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMembershipRepository circleMembershipRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private FundingService fundingService;

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

    private Circle createOverdueActiveCircle() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Test Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(2)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(false)
                .build();

        Circle circle = circleService.createCircle(creator.getId(), dto);
        circleService.joinCircle(member2.getId(), circle.getId());

        // Drain whoever round 1 paid out - their own payout would otherwise cover round 2's
        // contribution on its own, so neither member would actually be short.
        for (User member : List.of(creator, member2)) {
            BigDecimal balance = accountService.getUserAccount(member.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();
            if (balance.signum() > 0) {
                fundingService.postWithdrawal(member.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                        balance, UUID.randomUUID().toString());
            }
        }

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        reloaded.setLastRoundAt(Instant.now().minus(Duration.ofDays((reloaded.getTerm() + 1L) * 7)));
        return circleRepository.save(reloaded);
    }

    @Test
    void runDueRoundChecksProcessesRealOverdueCircles() {
        Circle circle = createOverdueActiveCircle();

        scheduler.runDueRoundChecks();

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DEFAULTED))
                .isNotEmpty();
    }

    @Test
    void oneCircleFailingDoesNotBlockOthersFromProcessing() {
        Circle circle = createOverdueActiveCircle();
        UUID nonExistentCircleId = UUID.randomUUID();

        // nonExistentCircleId fails immediately (Circle not found) - it comes first in the list
        // specifically to prove that failure doesn't stop the real circle after it from being tried
        scheduler.processCircles(List.of(nonExistentCircleId, circle.getId()));

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DEFAULTED))
                .isNotEmpty();
    }
}
