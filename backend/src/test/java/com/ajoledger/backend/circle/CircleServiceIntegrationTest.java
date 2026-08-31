package com.ajoledger.backend.circle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.exception.CircleJoinNotAllowedException;
import com.ajoledger.backend.circle.exception.InviteAlreadyRedeemedException;
import com.ajoledger.backend.circle.exception.InviteIdentityMismatchException;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
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
    private CircleSizingPolicy circleSizingPolicy;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private FundingService fundingService;

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
        CircleCreateRequestDTO dto = new CircleCreateRequestDTO();
        dto.setName("Test Circle");
        dto.setDescription("A circle for testing");
        dto.setMaxMembers(maxMembers);
        dto.setTerm(4);
        dto.setContributionAmount(new BigDecimal("100.00"));
        dto.setGated(gated);
        return dto;
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
    }

    @Test
    void gatedCircleBlocksJoinWithoutInvite() {
        User creator = createFundedUser(new BigDecimal("100.00"));
        User outsider = createFundedUser(new BigDecimal("100.00"));

        Circle circle = circleService.createCircle(creator.getId(), createRequest(2, true));

        assertThatThrownBy(() -> circleService.joinCircle(outsider.getId(), circle.getId()))
                .isInstanceOf(CircleJoinNotAllowedException.class);
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
        User creator = createFundedUser(new BigDecimal("100.00"));
        Circle openCircle = circleService.createCircle(creator.getId(), createRequest(4, false));
        Circle gatedCircle = circleService.createCircle(createFundedUser(new BigDecimal("100.00")).getId(),
                createRequest(4, true));

        BigDecimal openLoss = circleSizingPolicy.calculatePotentialGroupLoss(openCircle, creator, 1);
        BigDecimal gatedLoss = circleSizingPolicy.calculatePotentialGroupLoss(gatedCircle, creator, 1);

        assertThat(gatedLoss).isLessThan(openLoss);
    }
}
