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
import com.ajoledger.backend.transaction.exception.LienWithdrawalBlockedException;
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
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));

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
        User creator = createFundedUser(new BigDecimal("110.00"));
        User outsider = createFundedUser(new BigDecimal("110.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));

        assertThatThrownBy(() -> circleService.joinCircle(outsider.getId(), circle.getId()))
                .isInstanceOf(CircleActionNotAllowedException.class);
    }

    @Test
    void invitedByUsernameCanRedeemAndJoin() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User invitee = createFundedUser(new BigDecimal("110.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), invitee.getId());
        circleService.joinCircle(invitee.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }

    @Test
    void invitedByEmailForNewUserCanRedeemAfterSignupAndJoin() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        String futureEmail = "future-" + UUID.randomUUID() + "@example.com";

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), null, futureEmail);

        // the invited person didn't have an account at invite time - they sign up now
        User newUser = createUser(futureEmail);
        accountService.createUserAccount(newUser, UserAccountType.SAVINGS);
        accountService.createUserAccount(newUser, UserAccountType.LIEN);
        fundingService.postDeposit(newUser.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                new BigDecimal("110.00"), UUID.randomUUID().toString());

        circleService.redeemInvite(invite.getCode(), newUser.getId());
        circleService.joinCircle(newUser.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
    }

    @Test
    void redeemWithMismatchedIdentityIsRejected() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User invitee = createFundedUser(new BigDecimal("110.00"));
        User outsider = createFundedUser(new BigDecimal("110.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);

        assertThatThrownBy(() -> circleService.redeemInvite(invite.getCode(), outsider.getId()))
                .isInstanceOf(InviteIdentityMismatchException.class);
    }

    @Test
    void redeemingAlreadyRedeemedCodeIsRejected() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User invitee = createFundedUser(new BigDecimal("110.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, true));
        CircleInvite invite = circleService.inviteToCircle(circle.getId(), creator.getId(), invitee.getUsername(), null);
        circleService.redeemInvite(invite.getCode(), invitee.getId());

        assertThatThrownBy(() -> circleService.redeemInvite(invite.getCode(), invitee.getId()))
                .isInstanceOf(InviteAlreadyRedeemedException.class);
    }

    @Test
    void insufficientBalanceOnJoinRollsBackEntireJoin() {
        User creator = createFundedUser(new BigDecimal("110.00"));
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
    void joinCollateralIsHeldInLienAtJoinTime() {
        User joiner = createFundedUser(new BigDecimal("110.00"));
        BigDecimal savingsBefore = accountService.getUserAccount(joiner.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();
        BigDecimal lienBefore = accountService.getUserAccount(joiner.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();

        circleService.createCircle(joiner.getId(), createRequest(2, false));

        BigDecimal lienAfter = accountService.getUserAccount(joiner.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        BigDecimal savingsAfter = accountService.getUserAccount(joiner.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();

        BigDecimal collateralHeld = lienAfter.subtract(lienBefore);
        assertThat(collateralHeld).isPositive();
        // SAVINGS dropped by more than just the contribution amount - the collateral came out of it too.
        assertThat(savingsBefore.subtract(savingsAfter)).isEqualByComparingTo(
                new BigDecimal("100.00").add(collateralHeld));
    }

    @Test
    void gatedCircleRequiresLessJoinCollateralThanOpen() {
        User openCreator = createFundedUser(new BigDecimal("110.00"));
        BigDecimal openLienBefore = accountService.getUserAccount(openCreator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        circleService.createCircle(openCreator.getId(), createRequest(2, false));
        BigDecimal openCollateral = accountService.getUserAccount(openCreator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance().subtract(openLienBefore);

        User gatedCreator = createFundedUser(new BigDecimal("110.00"));
        BigDecimal gatedLienBefore = accountService.getUserAccount(gatedCreator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        circleService.createCircle(gatedCreator.getId(), createRequest(2, true));
        BigDecimal gatedCollateral = accountService.getUserAccount(gatedCreator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance().subtract(gatedLienBefore);

        assertThat(gatedCollateral).isLessThan(openCollateral);
    }

    @Test
    void insufficientSavingsForContributionPlusCollateralRollsBackTheWholeJoin() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User tightMember = createFundedUser(new BigDecimal("100.00")); // exactly the contribution, no room for collateral

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));

        assertThatThrownBy(() -> circleService.joinCircle(tightMember.getId(), circle.getId()))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE))
                .hasSize(1); // only the creator - tightMember's membership never persisted
        assertThat(accountService.getUserAccount(tightMember.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00"); // untouched - the whole join rolled back
    }

    @Test
    void gatedCircleDiscountsPotentialGroupLoss() {
        User openCreator = createFundedUser(new BigDecimal("110.00"));
        User openMember2 = createFundedUser(new BigDecimal("110.00"));
        Circle openCircle = circleService.createCircle(openCreator.getId(), createRequest(2, false));
        circleService.joinCircle(openMember2.getId(), openCircle.getId());

        User gatedCreator = createFundedUser(new BigDecimal("110.00"));
        User gatedMember2 = createFundedUser(new BigDecimal("110.00"));
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
        User overdueCreator = createFundedUser(new BigDecimal("110.00"));
        User overdueMember = createFundedUser(new BigDecimal("110.00"));
        Circle overdueCircle = circleService.createCircle(overdueCreator.getId(), createRequest(2, false));
        circleService.joinCircle(overdueMember.getId(), overdueCircle.getId());
        pushPastDeadline(overdueCircle.getId());

        User notDueCreator = createFundedUser(new BigDecimal("110.00"));
        User notDueMember = createFundedUser(new BigDecimal("110.00"));
        Circle notDueCircle = circleService.createCircle(notDueCreator.getId(), createRequest(2, false));
        circleService.joinCircle(notDueMember.getId(), notDueCircle.getId()); // just activated, not due yet

        Circle formingCircle = circleService.createCircle(createFundedUser(new BigDecimal("110.00")).getId(),
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
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));

        Circle circle = circleService.createCircle(creator.getId(),
                openEnrollmentRequest(2, null, Instant.now().plus(Duration.ofDays(30))));
        circleService.joinCircle(member2.getId(), circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.ACTIVE);
        assertThat(reloaded.getLockedMemberCount()).isEqualTo(2);
    }

    @Test
    void findCirclesWithClosedEnrollmentReturnsOnlyPastDeadlineFormingCircles() {
        Circle pastDeadlineCircle = circleService.createCircle(createFundedUser(new BigDecimal("110.00")).getId(),
                openEnrollmentRequest(5, null, Instant.now().plus(Duration.ofDays(30))));
        pushPastEnrollmentDeadline(pastDeadlineCircle.getId());

        Circle futureDeadlineCircle = circleService.createCircle(createFundedUser(new BigDecimal("110.00")).getId(),
                openEnrollmentRequest(5, null, Instant.now().plus(Duration.ofDays(30))));

        Circle fixedSizeCircle = circleService.createCircle(createFundedUser(new BigDecimal("110.00")).getId(),
                createRequest(2, false));

        List<UUID> closed = circleService.findCirclesWithClosedEnrollment();

        assertThat(closed).contains(pastDeadlineCircle.getId());
        assertThat(closed).doesNotContain(futureDeadlineCircle.getId(), fixedSizeCircle.getId());
    }

    @Test
    void closeEnrollmentActivatesWhenMinimumMet() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));

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
        User creator = createFundedUser(new BigDecimal("110.00"));
        BigDecimal savingsBefore = accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();

        Circle circle = circleService.createCircle(creator.getId(),
                openEnrollmentRequest(5, 3, Instant.now().plus(Duration.ofDays(30)))); // only the creator ends up joining
        BigDecimal lienAfterJoin = accountService.getUserAccount(creator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        pushPastEnrollmentDeadline(circle.getId());

        circleService.closeEnrollment(circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.DISSOLVED);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DISSOLVED))
                .hasSize(1);
        // contribution refunded to SAVINGS; the join-time LIEN collateral is untouched by dissolution,
        // still theirs and immediately withdrawable now that the membership is no longer ACTIVE.
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.LIEN).orElseThrow().getBalance())
                .isEqualByComparingTo(lienAfterJoin);
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo(savingsBefore.subtract(lienAfterJoin));
    }

    @Test
    void cancelCircleRefundsPartiallyFilledFixedSizeCircleInFull() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));
        BigDecimal creatorSavingsBefore = accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();
        BigDecimal member2SavingsBefore = accountService.getUserAccount(member2.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, false)); // room for 1 more
        circleService.joinCircle(member2.getId(), circle.getId());
        BigDecimal creatorLienAfterJoin = accountService.getUserAccount(creator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        BigDecimal member2LienAfterJoin = accountService.getUserAccount(member2.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();

        circleService.cancelCircle(circle.getId(), creator.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.DISSOLVED);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.DISSOLVED))
                .hasSize(2);
        // contribution refunded to SAVINGS; the join-time LIEN collateral stays put, untouched by dissolution.
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.LIEN).orElseThrow().getBalance())
                .isEqualByComparingTo(creatorLienAfterJoin);
        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo(creatorSavingsBefore.subtract(creatorLienAfterJoin));
        assertThat(accountService.getUserAccount(member2.getId(), UserAccountType.LIEN).orElseThrow().getBalance())
                .isEqualByComparingTo(member2LienAfterJoin);
        assertThat(accountService.getUserAccount(member2.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo(member2SavingsBefore.subtract(member2LienAfterJoin));
    }

    @Test
    void cancelCircleRejectsNonCreator() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User outsider = createFundedUser(new BigDecimal("110.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, false));

        assertThatThrownBy(() -> circleService.cancelCircle(circle.getId(), outsider.getId()))
                .isInstanceOf(CircleCreatorRequiredException.class);
    }

    @Test
    void cancelCircleRejectsAlreadyActiveCircle() {
        User creator = createFundedUser(new BigDecimal("110.00"));
        User member2 = createFundedUser(new BigDecimal("110.00"));
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

    @Test
    void positionCompactionAfterSingleWithdrawalResolvesCircleToCompletion() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User m2 = createFundedUser(new BigDecimal("110.00")); // enough for round 1 only
        User m3 = createFundedUser(new BigDecimal("500.00"));
        User m4 = createFundedUser(new BigDecimal("500.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(4, false));
        circleService.joinCircle(m2.getId(), circle.getId());
        circleService.joinCircle(m3.getId(), circle.getId());
        circleService.joinCircle(m4.getId(), circle.getId()); // activates, round 1 resolves

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // m2 can't pay round 2, withdraws, positions compact

        Circle afterWithdrawal = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(afterWithdrawal.getLockedMemberCount()).isEqualTo(3);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.WITHDRAWN))
                .extracting(m -> m.getUser().getId())
                .containsExactly(m2.getId());

        // Gapless 1..3, regardless of which rule ends up deciding who holds which slot.
        List<Integer> remainingPositions = circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE)
                .stream().map(CircleMembership::getPosition).sorted().toList();
        assertThat(remainingPositions).containsExactly(1, 2, 3);

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // the circle's now-final round: completes

        Circle finalCircle = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(finalCircle.getStatus()).isEqualTo(CircleStatus.COMPLETED);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.COMPLETED))
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(creator.getId(), m3.getId(), m4.getId());
    }

    @Test
    void simultaneousWithdrawalsInSameRoundCompactPositionsCorrectly() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User m2 = createFundedUser(new BigDecimal("110.00")); // enough for round 1 only
        User m3 = createFundedUser(new BigDecimal("110.00")); // enough for round 1 only
        User m4 = createFundedUser(new BigDecimal("500.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(4, false));
        circleService.joinCircle(m2.getId(), circle.getId());
        circleService.joinCircle(m3.getId(), circle.getId());
        circleService.joinCircle(m4.getId(), circle.getId()); // activates, round 1 resolves

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 2: both m2 and m3 can't pay, both withdraw

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getLockedMemberCount()).isEqualTo(2);
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.COMPLETED); // only creator and m4 remain, both resolved

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.WITHDRAWN))
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(m2.getId(), m3.getId());
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.COMPLETED))
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(creator.getId(), m4.getId());
        assertThat(circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circle.getId(), m4.getId())).isTrue();
    }

    @Test
    void completedCircleIgnoresFurtherProcessing() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User member2 = createFundedUser(new BigDecimal("500.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(member2.getId(), circle.getId()); // activates, round 1 resolves

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 2 resolves, circle completes

        Circle completed = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(CircleStatus.COMPLETED);

        BigDecimal creatorBalanceBefore = accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();
        BigDecimal member2BalanceBefore = accountService.getUserAccount(member2.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance();

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // must be a no-op

        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo(creatorBalanceBefore);
        assertThat(accountService.getUserAccount(member2.getId(), UserAccountType.SAVINGS).orElseThrow().getBalance())
                .isEqualByComparingTo(member2BalanceBefore);

        Circle stillCompleted = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(stillCompleted.getStatus()).isEqualTo(CircleStatus.COMPLETED);
        assertThat(stillCompleted.getCurrentRound()).isEqualTo(completed.getCurrentRound());
    }

    @Test
    void joiningSecondCircleWhileActiveInAnotherIsRejected() {
        User user = createFundedUser(new BigDecimal("500.00"));
        User otherCreator = createFundedUser(new BigDecimal("500.00"));

        circleService.createCircle(user.getId(), createRequest(2, false)); // still FORMING, user is ACTIVE in it

        Circle secondCircle = circleService.createCircle(otherCreator.getId(), createRequest(2, false));

        assertThatThrownBy(() -> circleService.joinCircle(user.getId(), secondCircle.getId()))
                .isInstanceOf(CircleActionNotAllowedException.class);
    }

    @Test
    void completedMemberCanJoinNewCircle() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User member2 = createFundedUser(new BigDecimal("500.00"));
        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(member2.getId(), circle.getId()); // activates, round 1 resolves

        pushPastDeadline(circle.getId());
        circleService.processRoundDeadline(circle.getId()); // round 2 resolves, circle completes

        assertThat(circleRepository.findById(circle.getId()).orElseThrow().getStatus())
                .isEqualTo(CircleStatus.COMPLETED);

        User newCircleCreator = createFundedUser(new BigDecimal("500.00"));
        Circle newCircle = circleService.createCircle(newCircleCreator.getId(), createRequest(2, false));

        CircleMembership membership = circleService.joinCircle(creator.getId(), newCircle.getId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    private CircleMembership activeMembershipFor(UUID circleId, UUID userId) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst().orElseThrow();
    }

    @Test
    void circleCompletesWhenLastPositionIsVacatedByWithdrawal() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User lastPositionMember = createFundedUser(new BigDecimal("110.00")); // enough for round 1 only

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(lastPositionMember.getId(), circle.getId()); // activates, creator (position 1) paid round 1

        pushPastDeadline(circle.getId());
        // lastPositionMember (position 2, the circle's last) can't pay round 2 and withdraws,
        // leaving nobody to shift into the vacated position.
        circleService.processRoundDeadline(circle.getId());

        Circle reloaded = circleRepository.findById(circle.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CircleStatus.COMPLETED);
        assertThat(reloaded.getLockedMemberCount()).isEqualTo(1);
        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.COMPLETED))
                .extracting(m -> m.getUser().getId())
                .containsExactly(creator.getId());
    }

    @Test
    void higherLienBalanceGetsEarlierPosition() {
        User richInLien = createFundedUser(new BigDecimal("500.00"));
        fundingService.postDeposit(richInLien.getId(), UserAccountType.LIEN, FundingType.STRIPE,
                new BigDecimal("200.00"), UUID.randomUUID().toString());
        User poorInLien = createFundedUser(new BigDecimal("500.00")); // LIEN stays at 0

        Circle circle = circleService.createCircle(poorInLien.getId(), createRequest(2, false));
        circleService.joinCircle(richInLien.getId(), circle.getId()); // joins second, activates

        // Despite joining second, richInLien's LIEN coverage outranks poorInLien's join-order edge.
        assertThat(activeMembershipFor(circle.getId(), richInLien.getId()).getPosition()).isEqualTo(1);
        assertThat(activeMembershipFor(circle.getId(), poorInLien.getId()).getPosition()).isEqualTo(2);
    }

    @Test
    void equalReputationBreaksTiesByJoinOrder() {
        User first = createFundedUser(new BigDecimal("500.00"));
        User second = createFundedUser(new BigDecimal("500.00"));

        Circle circle = circleService.createCircle(first.getId(), createRequest(2, false));
        circleService.joinCircle(second.getId(), circle.getId());

        assertThat(activeMembershipFor(circle.getId(), first.getId()).getPosition()).isEqualTo(1);
        assertThat(activeMembershipFor(circle.getId(), second.getId()).getPosition()).isEqualTo(2);
    }

    @Test
    void defaultPenalizedMoreThanWithdrawalInReputationScore() {
        // defaultingUser: paid out first in a past circle, then fails to pay again. Their LIEN
        // (credited from that payout) gets fully seized by the waterfall, ending at zero.
        User defaultingUser = createFundedUser(new BigDecimal("500.00"));
        User pastCoMember1 = createFundedUser(new BigDecimal("500.00"));
        Circle pastCircleA = circleService.createCircle(defaultingUser.getId(), createRequest(2, false));
        circleService.joinCircle(pastCoMember1.getId(), pastCircleA.getId());

        BigDecimal defaultingUserBalance = accountService.getUserAccount(defaultingUser.getId(), UserAccountType.SAVINGS)
                .orElseThrow().getBalance();
        fundingService.postWithdrawal(defaultingUser.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                defaultingUserBalance, UUID.randomUUID().toString());
        pushPastDeadline(pastCircleA.getId());
        circleService.processRoundDeadline(pastCircleA.getId());

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(pastCircleA.getId(), MembershipStatus.DEFAULTED))
                .extracting(m -> m.getUser().getId())
                .containsExactly(defaultingUser.getId());

        // withdrawingUser: joins second in a past circle (never paid), then fails before their turn.
        // Never credited any LIEN, so it stays at zero too, isolating the comparison to the penalty.
        User pastCoMember2 = createFundedUser(new BigDecimal("500.00"));
        User withdrawingUser = createFundedUser(new BigDecimal("110.00")); // enough for the join contribution only
        Circle pastCircleB = circleService.createCircle(pastCoMember2.getId(), createRequest(2, false));
        circleService.joinCircle(withdrawingUser.getId(), pastCircleB.getId());

        pushPastDeadline(pastCircleB.getId());
        circleService.processRoundDeadline(pastCircleB.getId());

        assertThat(circleMembershipRepository.findByCircle_IdAndStatus(pastCircleB.getId(), MembershipStatus.WITHDRAWN))
                .extracting(m -> m.getUser().getId())
                .containsExactly(withdrawingUser.getId());

        // Fresh SAVINGS for both (their old accounts persist across circles) to join a new one together.
        fundingService.postDeposit(defaultingUser.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                new BigDecimal("500.00"), UUID.randomUUID().toString());
        fundingService.postDeposit(withdrawingUser.getId(), UserAccountType.SAVINGS, FundingType.STRIPE,
                new BigDecimal("500.00"), UUID.randomUUID().toString());

        Circle newCircle = circleService.createCircle(defaultingUser.getId(), createRequest(2, false));
        circleService.joinCircle(withdrawingUser.getId(), newCircle.getId());

        // Same zero success count and zero LIEN for both; only the default-vs-withdrawal penalty differs.
        assertThat(activeMembershipFor(newCircle.getId(), withdrawingUser.getId()).getPosition()).isEqualTo(1);
        assertThat(activeMembershipFor(newCircle.getId(), defaultingUser.getId()).getPosition()).isEqualTo(2);
    }

    @Test
    void sufficientLienMeansNoSkimAtPayout() {
        User richInLien = createFundedUser(new BigDecimal("500.00"));
        fundingService.postDeposit(richInLien.getId(), UserAccountType.LIEN, FundingType.STRIPE,
                new BigDecimal("200.00"), UUID.randomUUID().toString());
        User other = createFundedUser(new BigDecimal("500.00"));

        Circle circle = circleService.createCircle(richInLien.getId(), createRequest(2, false));
        // captured after the join-time collateral hold has already landed, so this isolates the payout skim specifically
        BigDecimal lienAfterJoin = accountService.getUserAccount(richInLien.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        circleService.joinCircle(other.getId(), circle.getId()); // activates; richInLien outranks by LIEN, pays round 1
        assertThat(activeMembershipFor(circle.getId(), richInLien.getId()).getPosition()).isEqualTo(1);

        BigDecimal lienAfterPayout = accountService.getUserAccount(richInLien.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        assertThat(lienAfterPayout).isEqualByComparingTo(lienAfterJoin); // banked LIEN already covers the loss, no skim
    }

    @Test
    void capLimitsSkimEvenWhenNeededIsLarger() {
        User recipient = createFundedUser(new BigDecimal("500.00")); // creator, zero LIEN
        User m2 = createFundedUser(new BigDecimal("500.00"));
        User m3 = createFundedUser(new BigDecimal("500.00"));
        User m4 = createFundedUser(new BigDecimal("500.00"));
        int maxMembers = 4;
        BigDecimal contributionAmount = new BigDecimal("100.00");
        BigDecimal groupLossAtFirstPosition = TrustCalculations.calculateGroupLoss(contributionAmount, maxMembers, 1);

        Circle circle = circleService.createCircle(recipient.getId(), createRequest(maxMembers, false));
        circleService.joinCircle(m2.getId(), circle.getId());
        circleService.joinCircle(m3.getId(), circle.getId());
        circleService.joinCircle(m4.getId(), circle.getId()); // activates; all tied, creator keeps position 1 by join order
        assertThat(activeMembershipFor(circle.getId(), recipient.getId()).getPosition()).isEqualTo(1);

        BigDecimal lienAfter = accountService.getUserAccount(recipient.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        // capped short of the full need: some skim happened, but nowhere near the full group loss
        assertThat(lienAfter).isPositive().isLessThan(groupLossAtFirstPosition);
    }

    @Test
    void activeLockedCircleBlocksWithdrawalBelowFloor() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        User member2 = createFundedUser(new BigDecimal("500.00"));
        fundingService.postDeposit(member2.getId(), UserAccountType.LIEN, FundingType.STRIPE,
                new BigDecimal("200.00"), UUID.randomUUID().toString()); // plenty of headroom above the floor

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, false));
        circleService.joinCircle(member2.getId(), circle.getId()); // activates and locks the circle

        BigDecimal lienBalance = accountService.getUserAccount(member2.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        BigDecimal floor = circleService.computeLienWithdrawalFloor(member2.getId());
        BigDecimal oneDollarTooMuch = lienBalance.subtract(floor).add(BigDecimal.ONE);

        assertThatThrownBy(() -> circleService.postLienWithdrawal(member2.getId(), oneDollarTooMuch,
                UUID.randomUUID().toString()))
                .isInstanceOf(LienWithdrawalBlockedException.class);
    }

    @Test
    void formingCircleAllowsFullLienWithdrawalDespiteActiveMembership() {
        User creator = createFundedUser(new BigDecimal("500.00"));
        fundingService.postDeposit(creator.getId(), UserAccountType.LIEN, FundingType.STRIPE,
                new BigDecimal("50.00"), UUID.randomUUID().toString());

        Circle circle = circleService.createCircle(creator.getId(), createRequest(3, false)); // needs 3 members, still FORMING
        assertThat(circle.getStatus()).isEqualTo(CircleStatus.FORMING);

        // includes both the manual deposit above and the join-time collateral hold from createCircle's own join
        BigDecimal lienBalance = accountService.getUserAccount(creator.getId(), UserAccountType.LIEN)
                .orElseThrow().getBalance();
        circleService.postLienWithdrawal(creator.getId(), lienBalance, UUID.randomUUID().toString());

        assertThat(accountService.getUserAccount(creator.getId(), UserAccountType.LIEN).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noActiveMembershipAllowsFullLienWithdrawal() {
        User user = createFundedUser(new BigDecimal("500.00"));
        fundingService.postDeposit(user.getId(), UserAccountType.LIEN, FundingType.STRIPE,
                new BigDecimal("75.00"), UUID.randomUUID().toString());

        circleService.postLienWithdrawal(user.getId(), new BigDecimal("75.00"), UUID.randomUUID().toString());

        assertThat(accountService.getUserAccount(user.getId(), UserAccountType.LIEN).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Pushes a circle's enrollmentDeadline into the past. Raw SQL, since the field is
     * updatable=false and a normal save() would silently skip it. */
    private void pushPastEnrollmentDeadline(UUID circleId) {
        jdbcTemplate.update("UPDATE circle SET enrollment_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), circleId);
    }
}
