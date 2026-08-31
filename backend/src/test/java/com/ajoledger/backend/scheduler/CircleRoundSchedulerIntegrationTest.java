package com.ajoledger.backend.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));

        CircleCreateRequestDTO dto = new CircleCreateRequestDTO();
        dto.setName("Test Circle");
        dto.setDescription("A circle for testing");
        dto.setMaxMembers(2);
        dto.setTerm(4);
        dto.setContributionAmount(new BigDecimal("100.00"));
        dto.setGated(false);

        Circle circle = circleService.createCircle(creator.getId(), dto);
        circleService.joinCircle(member2.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        reloaded.setLastRoundAt(LocalDateTime.now().minusWeeks(reloaded.getTerm() + 1L));
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
