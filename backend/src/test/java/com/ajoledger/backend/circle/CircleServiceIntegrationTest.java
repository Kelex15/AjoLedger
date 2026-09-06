package com.ajoledger.backend.circle;

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
import java.util.Map;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.exception.CircleActionNotAllowedException;
import com.ajoledger.backend.circle.exception.CircleCreatorRequiredException;
import com.ajoledger.backend.circle.exception.InviteAlreadyRedeemedException;
import com.ajoledger.backend.circle.exception.InviteIdentityMismatchException;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CircleServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CircleService circleService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMembershipRepository circleMembershipRepository;
    @Autowired
    private CirclePayoutRepository circlePayoutRepository;
    @Autowired
    private Map<String, CircleSizingPolicy> sizingPoliciesByType;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private FundingService fundingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User createUser(String email) {
        User user = User.builder()
                .username("user" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("User")
                .email(email)
                .build();
        return userRepository.save(user);
    }

    /** Creates a user with SAVINGS/LIEN provisioned and SAVINGS funded, so joinCircle can succeed. */
    private User createFundedUser(BigDecimal savingsBalance) {
        User user = createUser("test-" + UUID.randomUUID() + "@example.com");
        accountService.createUserAccount(user, UserAccountType.SAVINGS);
        accountService.createUserAccount(user, UserAccountType.LIEN);
        fundingService.postDeposit(user.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                savingsBalance, UUID.randomUUID().toString());
        return user;
    }

    private CircleCreateRequestDTO createRequest(int maxMembers, boolean gated) {
        return CircleCreateRequestDTO.builder()
                .name("Test Circle")
                .description("A circle for testing")
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .maxMembers(maxMembers)
                .term(4)
                .contributionAmount(new BigDecimal("100.00"))
                .gated(gated)
                .build();
    }

    /** enrollmentDeadline bypasses the DTO's own lead-time validation here since that's only
     * enforced at the @Valid HTTP layer, not when calling CircleService directly. Lets tests
     * freely set an already-past or near-future deadline as each scenario needs. */
    private CircleCreateRequestDTO openEnrollmentRequest(int maxMembers, Integer minMembers, Instant enrollmentDeadline) {
        return createRequest(maxMembers, false).toBuilder()
                .sizingPolicyType(CircleSizingPolicyType.OPEN_ENROLLMENT)
                .minMembers(minMembers)
                .enrollmentDeadline(enrollmentDeadline)
                .build();
    }

    @Test
    void openCircleActivatesAndPaysRoundOneOnLastJoin() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(member2.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
        assertThat(reloaded.getCurrentRound()).isEqualTo(2);
        assertThat(reloaded.getLastRoundAt()).isNotNull();

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE))
                .allMatch(m -> m.getLastContributedRound() != null && m.getLastContributedRound() == 1);

        assertThat(circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circle.getId(), creator.getId())).isTrue();
        assertThat(accountService.getCircleAccount(circle.getId(), CircleAccountType.RESERVE).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void gatedCircleBlocksJoinWithoutInvite() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User outsider = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));

        assertThatThrownBy(() -> circleService.joinCircle(outsider.getId(), circle.getId()))
                .isInstanceOf(CircleActionNotAllowedException.class);
    }

    @Test
    void invitedByUsernameCanRedeemAndJoin() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User invitee = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), invitee.getId());
        circleService.joinCircle(invitee.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }

    @Test
    void invitedByEmailForNewUserCanRedeemAfterSignupAndJoin() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        String futureEmail = "future-" + UUID.randomUUID() + "@example.com";

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), null, futureEmail);

        // the invited person didn't have an account at invite time - they sign up now
        User newUser = createUser(futureEmail);
        accountService.createUserAccount(newUser, UserAccountType.SAVINGS);
        accountService.createUserAccount(newUser, UserAccountType.LIEN);
        fundingService.postDeposit(newUser.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                new BigDecimal("100.00"), UUID.randomUUID().toString());

        circleService.redeemInvite(invite.getCode(), newUser.getId());
        circleService.joinCircle(newUser.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }

    @Test
    void redeemWithMismatchedIdentityIsRejected() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User invitee = createFundedUser(new BigDecimal("100.00"));
        User outsider = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);

        assertThatThrownBy(() -> circleService.redeemInvite(invite.getCode(), outsider.getId()))
                .isInstanceOf(InviteIdentityMismatchException.class);
    }

    @Test
    void redeemingAlreadyRedeemedCodeIsRejected() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User invitee = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), invitee.getId());

        assertThatThrownBy(() -> circleService.redeemInvite(invite.getCode(), invitee.getId()))
                .isInstanceOf(InviteAlreadyRedeemedException.class);
    }

    @Test
    void insufficientBalanceOnJoinRollsBackEntireJoin() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User poorMember = createUser("test-" + UUID.randomUUID() + "@example.com");
        accountService.createUserAccount(poorMember, UserAccountType.SAVINGS);
        accountService.createUserAccount(poorMember, UserAccountType.LIEN);

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));

        assertThatThrownBy(() -> circleService.joinCircle(poorMember.getId(), circle.getId()))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE))
                .hasSize(1); // only the creator - poorMember's membership never persisted

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.FORMING);
        assertThat(reloaded.getCurrentRound()).isEqualTo(1);
    }

    @Test
    void gatedCircleDiscountsPotentialGroupLoss() {
        User openCreator = createFundedUser(new BigDecimal("100.00"));
        User openMember2 = createFundedUser(new BigDecimal("100.00"));
        Circle openCircle = circleService.createCircle(openCreator.getId(), createRequest(2, false));
        circleService.joinCircle(openMember2.getId(), openCircle.getId());

        User gatedCreator = createFundedUser(new BigDecimal("100.00"));
        User gatedMember2 = createFundedUser(new BigDecimal("100.00"));
        Circle gatedCircle = circleService.createCircle(gatedCreator.getId(), createRequest(2, true));
        CircleInvite invite = circleService.inviteToCircle(gatedCircle.getId(), gatedCreator.getId(), gatedMember2.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), gatedMember2.getId());
        circleService.joinCircle(gatedMember2.getId(), gatedCircle.getId());

        Circle reloadedOpen = circleRepository.findById(openCircle.getId()).orElseThrow();
        Circle reloadedGated = circleRepository.findById(gatedCircle.getId()).orElseThrow();

        CircleSizingPolicy fixedSizePolicy = sizingPoliciesByType.get("FIXED_SIZE");
        BigDecimal openLoss = fixedSizePolicy.calculatePotentialGroupLoss(reloadedOpen, openCreator, 1);
        BigDecimal gatedLoss = fixedSizePolicy.calculatePotentialGroupLoss(reloadedGated, gatedCreator, 1);

        assertThat(gatedLoss).isLessThan(openLoss);
    }

    @Test
    void findCirclesWithRoundsDueTodayReturnsOnlyOverdueActiveCircles() {
        User overdueCreator = createFundedUser(new BigDecimal("100.00"));
        User overdueMember = createFundedUser(new BigDecimal("100.00"));
        Circle overdueCircle = circleService.createCircle(overdueCreator.getId(), createRequest(2, false));
        circleService.joinCircle(overdueMember.getId(), overdueCircle.getId());
        pushPastDeadline(overdueCircle.getId());

        User notDueCreator = createFundedUser(new BigDecimal("100.00"));
        User notDueMember = createFundedUser(new BigDecimal("100.00"));
        Circle notDueCircle = circleService.createCircle(notDueCreator.getId(), createRequest(2, false));
        circleService.joinCircle(notDueMember.getId(), notDueCircle.getId()); // just activated, not due yet

        Circle formingCircle = circleService.createCircle(createFundedUser(new BigDecimal("100.00")).getId(),
                createRequest(2, false)); // only the creator joined - still FORMING

        List<UUID> due = circleService.findCirclesWithRoundsDueToday();

        assertThat(due).contains(overdueCircle.getId());
        assertThat(due).doesNotContain(notDueCircle.getId(), formingCircle.getId());
    }

    /** Pushes a circle's lastRoundAt far enough into the past that its current round counts as overdue. */
    private void pushPastDeadline(UUID circleId) {
        Circle circle = circleRepository.findById(circleId).orElseThrow();
        circle.setLastRoundAt(Instant.now().minus(Duration.ofDays((circle.getTerm() + 1L) * 7)));
        circleRepository.save(circle);
    }

    @Test
    void openEnrollmentCircleActivatesEarlyWhenCapHit() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(),
                openEnrollmentRequest(2, null, Instant.now().plus(Duration.ofDays(30))));
        circleService.joinCircle(member2.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
        assertThat(reloaded.getLockedMemberCount()).isEqualTo(2);
    }

    @Test
    void findCirclesWithClosedEnrollmentReturnsOnlyPastDeadlineFormingCircles() {
        Circle pastDeadlineCircle = circleService.createCircle(createFundedUser(new BigDecimal("100.00")).getId(),
                openEnrollmentRequest(5, null, Instant.now().plus(Duration.ofDays(30))));
        pushPastEnrollmentDeadline(pastDeadlineCircle.getId());

        Circle futureDeadlineCircle = circleService.createCircle(createFundedUser(new BigDecimal("100.00")).getId(),
                openEnrollmentRequest(5, null, Instant.now().plus(Duration.ofDays(30))));

        Circle fixedSizeCircle = circleService.createCircle(createFundedUser(new BigDecimal("100.00")).getId(),
                createRequest(2, false));

        List<UUID> closed = circleService.findCirclesWithClosedEnrollment();

        assertThat(closed).contains(pastDeadlineCircle.getId());
        assertThat(closed).doesNotContain(futureDeadlineCircle.getId(), fixedSizeCircle.getId());
    }

    @Test
    void closeEnrollmentActivatesWhenMinimumMet() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(),
                openEnrollmentRequest(5, 2, Instant.now().plus(Duration.ofDays(30))));
        circleService.joinCircle(member2.getId(), circle.getId());
        pushPastEnrollmentDeadline(circle.getId());

        circleService.closeEnrollment(circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
        assertThat(reloaded.getLockedMemberCount()).isEqualTo(2);
        assertThat(circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circle.getId(), creator.getId())).isTrue();
    }

    @Test
    void closeEnrollmentDissolvesAndRefundsInFullWhenMinimumNotMet() {
        User creator = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(),
                openEnrollmentRequest(5, 3, Instant.now().plus(Duration.ofDays(30)))); // only the creator ends up joining
        pushPastEnrollmentDeadline(circle.getId());

        circleService.closeEnrollment(circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.DISSOLVED);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DISSOLVED))
                .hasSize(1);
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00"); // refunded in full, back to their pre-join balance
    }

    @Test
    void cancelCircleRefundsPartiallyFilledFixedSizeCircleInFull() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, false)); // room for 1 more
        circleService.joinCircle(member2.getId(), circle.getId());

        circleService.cancelCircle(circle.getId(), creator.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.DISSOLVED);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DISSOLVED))
                .hasSize(2);
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
        assertThat(accountService.getUserAccount(member2.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void cancelCircleRejectsNonCreator() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User outsider = createFundedUser(new BigDecimal("100.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, false));

        assertThatThrownBy(() -> circleService.cancelCircle(circle.getId(), outsider.getId()))
                .isInstanceOf(CircleCreatorRequiredException.class);
    }

    @Test
    void cancelCircleRejectsAlreadyActiveCircle() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User member2 = createFundedUser(new BigDecimal("100.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(member2.getId(), circle.getId()); // fills it, activates

        assertThatThrownBy(() -> circleService.cancelCircle(circle.getId(), creator.getId()))
                .isInstanceOf(CircleActionNotAllowedException.class);
    }

    @Test
    void defaultRecoveryIsSpreadAcrossFutureRoundsViaReserve() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User m2 = createFundedUser(new BigDecimal("500.00"));
        User m3 = createFundedUser(new BigDecimal("500.00"));
        User m4 = createFundedUser(new BigDecimal("500.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(4, false));
        circleService.joinCircle(m2.getId(), circle.getId());
        circleService.joinCircle(m3.getId(), circle.getId());
        circleService.joinCircle(m4.getId(), circle.getId()); // activates, round 1 resolves, creator paid

        // Drain the creator's SAVINGS so round 2's contribution sweep fails, forcing a default.
        BigDecimal creatorBalance = accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();
        fundingService.postWithdrawal(creator.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                creatorBalance, UUID.randomUUID().toString());

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 2: creator defaults, reserve credited, m2 paid

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DEFAULTED))
                .extracting(m -> m.getUser().getId())
                .containsExactly(creator.getId());

        BigDecimal reserveAfterRound2 = reserveBalance(circle.getId());
        assertThat(reserveAfterRound2).isGreaterThan(BigDecimal.ZERO);

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 3: m3 paid, another share released

        BigDecimal reserveAfterRound3 = reserveBalance(circle.getId());
        assertThat(reserveAfterRound3).isLessThan(reserveAfterRound2).isGreaterThan(BigDecimal.ZERO);

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 4, the circle's last round: fully drains

        assertThat(reserveBalance(circle.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private BigDecimal reserveBalance(UUID circleId) {
        return accountService.getCircleAccount(circleId, CircleAccountType.RESERVE).orElseThrow().getBalance();
    }

    /** Pushes a circle's enrollmentDeadline into the past. Raw SQL, since the field is
     * updatable=false and a normal save() would silently skip it. */
    private void pushPastEnrollmentDeadline(UUID circleId) {
        jdbcTemplate.update("UPDATE circle SET enrollment_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), circleId);
    }
}
