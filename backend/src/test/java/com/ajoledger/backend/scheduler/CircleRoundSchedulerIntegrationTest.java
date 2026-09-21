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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleInvite;
import com.ajoledger.backend.circle.CircleMemberDebitAuthorization;
import com.ajoledger.backend.circle.CircleMemberDebitAuthorizationRepository;
import com.ajoledger.backend.circle.CircleMembership;
import com.ajoledger.backend.circle.CircleMembershipRepository;
import com.ajoledger.backend.circle.CircleRepository;
import com.ajoledger.backend.circle.CircleService;
import com.ajoledger.backend.circle.CircleTransactionsService;
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
    @Autowired
    private CircleTransactionsService circleTransactionsService;
    @Autowired
    private CircleMemberDebitAuthorizationRepository circleMemberDebitAuthorizationRepository;

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
    void defaultOnGatedCircleStillSeizesFullUndiscountedLien() {
        User memberA = createFundedUser(new BigDecimal("1000.00"));
        User memberB = createFundedUser(new BigDecimal("1000.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Gated Circle")
                .description("A gated circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(2)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(true)
                .build();

        Circle circle = circleService.createCircle(memberA.getId(), dto);
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), memberA.getId(), memberB.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), memberB.getId());
        circleService.joinCircle(memberB.getId(), circle.getId());

        CircleMembership recipientMembership = circleMembershipRepository
                .findByCircle_IdAndUser_IdAndStatus(circle.getId(), memberA.getId(), MembershipStatus.ACTIVE)
                .filter(m -> m.getPosition() != null && m.getPosition() == 1)
                .orElseGet(() -> circleMembershipRepository
                        .findByCircle_IdAndUser_IdAndStatus(circle.getId(), memberB.getId(), MembershipStatus.ACTIVE)
                        .orElseThrow());
        UUID recipientId = recipientMembership.getUser().getId();

        // Top up the round-1 recipient's LIEN well past the $100 group loss (contributionAmount x
        // one remaining round), so a correct waterfall has more than enough to seize in full.
        circleTransactionsService.postLienHold(recipientId, new BigDecimal("100.00"), UUID.randomUUID().toString());

        for (User member : List.of(memberA, memberB)) {
            BigDecimal balance = accountService.getUserAccount(member.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();
            if (balance.signum() > 0) {
                fundingService.postWithdrawal(member.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                        balance, UUID.randomUUID().toString());
            }
        }

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        reloaded.setLastRoundAt(Instant.now().minus(Duration.ofDays((reloaded.getTerm() + 1L) * 7)));
        circleRepository.save(reloaded);

        BigDecimal lienBeforeDefault = accountService.getUserAccount(recipientId, UserAccountType.LIEN).orElseThrow().getBalance();

        scheduler.runDueRoundChecks();

        BigDecimal lienAfterDefault = accountService.getUserAccount(recipientId, UserAccountType.LIEN).orElseThrow().getBalance();

        assertThat(circleMembershipRepository.findByCircle_IdAndUser_IdAndStatus(circle.getId(), recipientId, MembershipStatus.DEFAULTED))
                .isPresent();
        // Gating discounts the *forward-looking* risk estimate used for admission/collateral sizing,
        // not the real debt once a default has actually happened - the group is still short the full
        // $100 contribution regardless of how the circle was formed.
        assertThat(lienBeforeDefault.subtract(lienAfterDefault)).isEqualByComparingTo("100.00");
    }

    @Test
    void coOccurringWithdrawalDoesNotZeroOutSiblingDefaultsGroupLoss() {
        // Processing order per circle is effectively random, so many circles in one run samples
        // both orderings instead of gambling on one.
        int circleCount = 10;
        Map<UUID, BigDecimal> lienBeforeDefaultByUserId = new HashMap<>();

        for (int i = 0; i < circleCount; i++) {
            User recipient = createFundedUser(new BigDecimal("1000.00"));
            User withdrawer = createFundedUser(new BigDecimal("1000.00"));

            CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                    .name("Ordering Circle " + i)
                    .description("A circle for testing")
                    .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                    .maxMembers(2)
                    .term(4)
                    .contributionAmount(new BigDecimal("100.00"))
                    .gated(false)
                    .build();

            Circle circle = circleService.createCircle(recipient.getId(), dto);
            circleService.joinCircle(withdrawer.getId(), circle.getId()); // activates, round 1 pays recipient

            circleTransactionsService.postLienHold(recipient.getId(), new BigDecimal("100.00"), UUID.randomUUID().toString());

            for (User member : List.of(recipient, withdrawer)) {
                BigDecimal balance = accountService.getUserAccount(member.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();
                fundingService.postWithdrawal(member.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                        balance, UUID.randomUUID().toString());
            }

            Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
            reloaded.setLastRoundAt(Instant.now().minus(Duration.ofDays((reloaded.getTerm() + 1L) * 7)));
            circleRepository.save(reloaded);

            BigDecimal lienBeforeDefault = accountService.getUserAccount(recipient.getId(), UserAccountType.LIEN).orElseThrow().getBalance();
            lienBeforeDefaultByUserId.put(recipient.getId(), lienBeforeDefault);
        }

        scheduler.runDueRoundChecks();

        for (Map.Entry<UUID, BigDecimal> entry : lienBeforeDefaultByUserId.entrySet()) {
            BigDecimal lienAfterDefault = accountService.getUserAccount(entry.getKey(), UserAccountType.LIEN).orElseThrow().getBalance();
            assertThat(entry.getValue().subtract(lienAfterDefault)).as("lien seized for defaulter %s", entry.getKey())
                    .isEqualByComparingTo("100.00");
        }
    }

    @Test
    void ceilingMatchesLiveExposureRightAfterLock() {
        User memberA = createFundedUser(new BigDecimal("110.00"));
        User memberB = createFundedUser(new BigDecimal("110.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Two Member Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(2)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(false)
                .build();

        Circle circle = circleService.createCircle(memberA.getId(), dto);
        circleService.joinCircle(memberB.getId(), circle.getId()); // locks + resolves round 1

        List<CircleMembership> activeMembers = circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE);
        assertThat(activeMembers).hasSize(2);
        for (CircleMembership membership : activeMembers) {
            CircleMemberDebitAuthorization authorization = circleMemberDebitAuthorizationRepository
                    .findByCircleMembership_Id(membership.getId()).orElseThrow();
            assertThat(authorization.getRemainingCeiling()).isEqualByComparingTo("100.00");
        }
    }

    @Test
    void ceilingShrinksAfterARoundResolvesWithoutDefault() {
        User memberA = createFundedUser(new BigDecimal("1000.00"));
        User memberB = createFundedUser(new BigDecimal("1000.00"));
        User memberC = createFundedUser(new BigDecimal("1000.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Three Member Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(3)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(false)
                .build();

        Circle circle = circleService.createCircle(memberA.getId(), dto);
        circleService.joinCircle(memberB.getId(), circle.getId());
        circleService.joinCircle(memberC.getId(), circle.getId()); // locks + resolves round 1

        CircleMembership someMembership = circleMembershipRepository
                .findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).get(0);
        BigDecimal ceilingAfterRound1 = circleMemberDebitAuthorizationRepository
                .findByCircleMembership_Id(someMembership.getId()).orElseThrow().getRemainingCeiling();

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        reloaded.setLastRoundAt(Instant.now().minus(Duration.ofDays((reloaded.getTerm() + 1L) * 7)));
        circleRepository.save(reloaded);

        scheduler.runDueRoundChecks(); // everyone still has enough savings to pay round 2 - no default

        BigDecimal ceilingAfterRound2 = circleMemberDebitAuthorizationRepository
                .findByCircleMembership_Id(someMembership.getId()).orElseThrow().getRemainingCeiling();

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DEFAULTED)).isEmpty();
        assertThat(ceilingAfterRound2).isLessThan(ceilingAfterRound1);
        assertThat(ceilingAfterRound2).isEqualByComparingTo("100.00");
    }

    @Test
    void defaultSeizureIsCappedByCeilingWhenSmallerThanGroupLoss() {
        User memberA = createFundedUser(new BigDecimal("1000.00"));
        User memberB = createFundedUser(new BigDecimal("1000.00"));

        CircleCreateRequestDTO dto = CircleCreateRequestDTO.builder()
                .name("Capped Ceiling Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(2)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(false)
                .build();

        Circle circle = circleService.createCircle(memberA.getId(), dto);
        circleService.joinCircle(memberB.getId(), circle.getId());

        CircleMembership recipientMembership = circleMembershipRepository
                .findByCircle_IdAndUser_IdAndStatus(circle.getId(), memberA.getId(), MembershipStatus.ACTIVE)
                .filter(m -> m.getPosition() != null && m.getPosition() == 1)
                .orElseGet(() -> circleMembershipRepository
                        .findByCircle_IdAndUser_IdAndStatus(circle.getId(), memberB.getId(), MembershipStatus.ACTIVE)
                        .orElseThrow());
        UUID recipientId = recipientMembership.getUser().getId();

        circleTransactionsService.postLienHold(recipientId, new BigDecimal("100.00"), UUID.randomUUID().toString());

        // Simulate the ceiling having less headroom than the $100 group loss - e.g. a member who
        // already partly serviced their exposure through some other path this test doesn't model.
        CircleMemberDebitAuthorization authorization = circleMemberDebitAuthorizationRepository
                .findByCircleMembership_Id(recipientMembership.getId()).orElseThrow();
        authorization.setRemainingCeiling(new BigDecimal("40.00"));
        circleMemberDebitAuthorizationRepository.save(authorization);

        for (User member : List.of(memberA, memberB)) {
            BigDecimal balance = accountService.getUserAccount(member.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();
            if (balance.signum() > 0) {
                fundingService.postWithdrawal(member.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                        balance, UUID.randomUUID().toString());
            }
        }

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        reloaded.setLastRoundAt(Instant.now().minus(Duration.ofDays((reloaded.getTerm() + 1L) * 7)));
        circleRepository.save(reloaded);

        BigDecimal lienBeforeDefault = accountService.getUserAccount(recipientId, UserAccountType.LIEN).orElseThrow().getBalance();

        scheduler.runDueRoundChecks();

        BigDecimal lienAfterDefault = accountService.getUserAccount(recipientId, UserAccountType.LIEN).orElseThrow().getBalance();

        assertThat(circleMembershipRepository.findByCircle_IdAndUser_IdAndStatus(circle.getId(), recipientId, MembershipStatus.DEFAULTED))
                .isPresent();
        // Capped at the $40 ceiling, not the full $100 group loss.
        assertThat(lienBeforeDefault.subtract(lienAfterDefault)).isEqualByComparingTo("40.00");
        assertThat(circleMemberDebitAuthorizationRepository.findByCircleMembership_Id(recipientMembership.getId())
                .orElseThrow().getRemainingCeiling()).isEqualByComparingTo("0.00");
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
