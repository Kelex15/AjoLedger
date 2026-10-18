package com.ajoledger.backend.ledger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ajoledger.backend.BackendApplication;
import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.AccountRepository;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.CircleTransactionsService;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.entity.CircleMemberDebitAuthorization;
import com.ajoledger.backend.circle.entity.CircleMembership;
import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.circle.repository.CircleMemberDebitAuthorizationRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;
import com.ajoledger.backend.circle.repository.CircleRepository;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** jqwik runs on its own JUnit Platform engine, not Jupiter, so the Jupiter-only
 * {@code @Testcontainers}/{@code @SpringBootTest} extensions don't apply here; the Spring
 * context and the Postgres container are bootstrapped manually instead. */
class LedgerPropertyTest {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("100000.00");

    private static PostgreSQLContainer<?> postgres;
    private static ConfigurableApplicationContext context;

    private static LedgerService ledgerService;
    private static LedgerDao ledgerDao;
    private static JdbcTemplate jdbcTemplate;
    private static AccountService accountService;
    private static AccountRepository accountRepository;
    private static UserAccountRepository userAccountRepository;
    private static FundingService fundingService;
    private static UserRepository userRepository;
    private static CircleTransactionsService circleTransactionsService;
    private static CircleRepository circleRepository;
    private static CircleMembershipRepository circleMembershipRepository;
    private static CircleMemberDebitAuthorizationRepository circleMemberDebitAuthorizationRepository;

    @BeforeContainer
    static void startUp() {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();

        context = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--DATABASE_URL=" + postgres.getJdbcUrl(),
                        "--DATABASE_USERNAME=" + postgres.getUsername(),
                        "--DATABASE_PASSWORD=" + postgres.getPassword()
                );

        ledgerService = context.getBean(LedgerService.class);
        ledgerDao = context.getBean(LedgerDao.class);
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        accountService = context.getBean(AccountService.class);
        accountRepository = context.getBean(AccountRepository.class);
        userAccountRepository = context.getBean(UserAccountRepository.class);
        fundingService = context.getBean(FundingService.class);
        userRepository = context.getBean(UserRepository.class);
        circleTransactionsService = context.getBean(CircleTransactionsService.class);
        circleRepository = context.getBean(CircleRepository.class);
        circleMembershipRepository = context.getBean(CircleMembershipRepository.class);
        circleMemberDebitAuthorizationRepository = context.getBean(CircleMemberDebitAuthorizationRepository.class);
    }

    @AfterContainer
    static void tearDown() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static User createUser() {
        User user = User.builder()
                .username("puser" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Prop")
                .lastName("Test")
                .email("prop-" + UUID.randomUUID() + "@example.com")
                .build();
        return userRepository.save(user);
    }

    private static UserAccount createFundedAccount(User user, UserAccountType type, BigDecimal balance) {
        UserAccount account = accountService.createUserAccount(user, type);
        if (balance.signum() > 0) {
            fundingService.postDeposit(user.getId(), type, FundingType.STRIPE, balance, UUID.randomUUID().toString());
        }
        return account;
    }

    private static BigDecimal centsToAmount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    // ---- Random valid transfer sequences (properties 1-3) ----

    private record TransferStep(int fromIndex, int toOffset, int permille) {
    }

    private record AccountRef(UUID userId, UserAccountType type, UUID accountId) {
    }

    private record ExecutedTransfer(AccountRef from, AccountRef to, BigDecimal amount, String idempotencyKey,
                                     UUID transactionId) {
    }

    private record SequenceRun(List<UUID> transactionIds, List<ExecutedTransfer> executed) {
    }

    @Provide
    Arbitrary<List<TransferStep>> transferSequences() {
        Arbitrary<Integer> fromIndex = Arbitraries.integers().between(0, 3);
        Arbitrary<Integer> toOffset = Arbitraries.integers().between(1, 3);
        Arbitrary<Integer> permille = Arbitraries.integers().between(0, 1000);
        return Combinators.combine(fromIndex, toOffset, permille)
                .as(TransferStep::new)
                .list().ofMinSize(1).ofMaxSize(15);
    }

    /** Runs the sequence through the real LedgerService against fresh accounts, asserting after
     * every transfer that balances match the ledger and never go negative (invariant 2). Each
     * amount is a fraction of the sender's current balance, so every transfer is guaranteed valid. */
    private SequenceRun runSequence(List<TransferStep> steps) {
        User u1 = createUser();
        User u2 = createUser();
        List<AccountRef> refs = List.of(
                new AccountRef(u1.getId(), UserAccountType.SAVINGS,
                        createFundedAccount(u1, UserAccountType.SAVINGS, STARTING_BALANCE).getId()),
                new AccountRef(u1.getId(), UserAccountType.CHECKING,
                        createFundedAccount(u1, UserAccountType.CHECKING, STARTING_BALANCE).getId()),
                new AccountRef(u2.getId(), UserAccountType.SAVINGS,
                        createFundedAccount(u2, UserAccountType.SAVINGS, STARTING_BALANCE).getId()),
                new AccountRef(u2.getId(), UserAccountType.CHECKING,
                        createFundedAccount(u2, UserAccountType.CHECKING, STARTING_BALANCE).getId())
        );

        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (AccountRef ref : refs) {
            balances.put(ref.accountId(), STARTING_BALANCE);
        }

        List<UUID> transactionIds = new ArrayList<>();
        List<ExecutedTransfer> executed = new ArrayList<>();

        for (TransferStep step : steps) {
            AccountRef from = refs.get(step.fromIndex());
            AccountRef to = refs.get((step.fromIndex() + step.toOffset()) % refs.size());
            BigDecimal fromBalance = balances.get(from.accountId());
            if (fromBalance.signum() <= 0) {
                continue; // nothing left to move out of this account, skip the step
            }
            BigDecimal amount = fromBalance.multiply(BigDecimal.valueOf(step.permille()))
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.DOWN);
            if (amount.signum() == 0) {
                amount = new BigDecimal("0.01"); // a real transfer always moves a positive amount
            }
            String idempotencyKey = UUID.randomUUID().toString();

            LedgerTransaction transaction = ledgerService.postTransfer(
                    from.userId(), from.type(), to.userId(), to.type(), amount, idempotencyKey);

            balances.merge(from.accountId(), amount.negate(), BigDecimal::add);
            balances.merge(to.accountId(), amount, BigDecimal::add);
            transactionIds.add(transaction.id());
            executed.add(new ExecutedTransfer(from, to, amount, idempotencyKey, transaction.id()));

            for (AccountRef ref : refs) {
                BigDecimal expected = balances.get(ref.accountId());
                assertThat(expected.signum()).isGreaterThanOrEqualTo(0);
                assertThat(accountRepository.findById(ref.accountId()).orElseThrow().getBalance())
                        .isEqualByComparingTo(expected);
                assertThat(ledgerDao.getBalance(ref.accountId())).isEqualByComparingTo(expected);
            }
        }

        return new SequenceRun(transactionIds, executed);
    }

    /** Invariant 1: every transaction posted across the sequence balances. Invariant 2 is
     * asserted inside runSequence, after each individual transfer. */
    @Property(tries = 30)
    void debitsAlwaysEqualCreditsAndNoBalanceGoesNegative(@ForAll("transferSequences") List<TransferStep> steps) {
        SequenceRun run = runSequence(steps);

        List<UUID> ids = run.transactionIds();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        BigDecimal totalDebit = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(debit_amount), 0) FROM ledger_entry WHERE transaction_id IN (" + placeholders + ")",
                BigDecimal.class, ids.toArray());
        BigDecimal totalCredit = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(credit_amount), 0) FROM ledger_entry WHERE transaction_id IN (" + placeholders + ")",
                BigDecimal.class, ids.toArray());

        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    }

    /** Invariant 3: replaying an already-succeeded request with its own idempotency key returns
     * the cached transaction and leaves every balance untouched. */
    @Property(tries = 30)
    void idempotencyReplayNeverChangesBalances(@ForAll("transferSequences") List<TransferStep> steps) {
        SequenceRun run = runSequence(steps);
        ExecutedTransfer last = run.executed().get(run.executed().size() - 1);

        BigDecimal fromBefore = ledgerDao.getBalance(last.from().accountId());
        BigDecimal toBefore = ledgerDao.getBalance(last.to().accountId());

        LedgerTransaction replay = ledgerService.postTransfer(
                last.from().userId(), last.from().type(), last.to().userId(), last.to().type(),
                last.amount(), last.idempotencyKey());

        assertThat(replay.id()).isEqualTo(last.transactionId());
        assertThat(ledgerDao.getBalance(last.from().accountId())).isEqualByComparingTo(fromBefore);
        assertThat(ledgerDao.getBalance(last.to().accountId())).isEqualByComparingTo(toBefore);
    }

    // ---- Default waterfall debit ceiling (property 4) ----

    @Provide
    Arbitrary<Long> nonNegativeCents() {
        return Arbitraries.longs().between(0, 10_000_00L);
    }

    /** Invariant 4: whatever SAVINGS/LIEN/CHECKING hold and whatever the group loss is, the
     * waterfall can never seize more in total than the member's remaining debit ceiling, since
     * seize()'s budget has to chain across all three legs rather than reset per account. */
    @Property(tries = 50)
    void defaultWaterfallNeverSeizesMoreThanRemainingDebitCeiling(
            @ForAll("nonNegativeCents") long savingsCents,
            @ForAll("nonNegativeCents") long lienCents,
            @ForAll("nonNegativeCents") long checkingCents,
            @ForAll("nonNegativeCents") long groupLossCents,
            @ForAll("nonNegativeCents") long ceilingCents) {

        BigDecimal savingsBalance = centsToAmount(savingsCents);
        BigDecimal lienBalance = centsToAmount(lienCents);
        BigDecimal checkingBalance = centsToAmount(checkingCents);
        BigDecimal groupLoss = centsToAmount(groupLossCents);
        BigDecimal ceiling = centsToAmount(ceilingCents);

        User user = createUser();
        createFundedAccount(user, UserAccountType.SAVINGS, savingsBalance);
        createFundedAccount(user, UserAccountType.LIEN, lienBalance);
        createFundedAccount(user, UserAccountType.CHECKING, checkingBalance);

        Circle circle = circleRepository.save(Circle.builder()
                .name("Prop Circle")
                .description("property test circle")
                .maxMembers(1)
                .term(4)
                .createdBy(user)
                .contributionAmount(new BigDecimal("10.00"))
                .currentRound(1)
                .status(CircleStatus.ACTIVE)
                .sizingPolicyType(CircleSizingPolicyType.FIXED_SIZE)
                .gated(false)
                .build());

        CircleMembership membership = circleMembershipRepository.save(CircleMembership.builder()
                .circle(circle)
                .user(user)
                .position(1)
                .status(MembershipStatus.ACTIVE)
                .build());

        CircleAccount reserve = accountService.createCircleAccount(circle, CircleAccountType.RESERVE);

        circleMemberDebitAuthorizationRepository.save(CircleMemberDebitAuthorization.builder()
                .circleMembership(membership)
                .remainingCeiling(ceiling)
                .build());

        circleTransactionsService.postDefaultWaterfall(
                user.getId(), membership.getId(), reserve.getId(), groupLoss, UUID.randomUUID().toString());

        BigDecimal savingsAfter = ledgerDao.getBalance(
                userAccountRepository.requireByUserIdAndType(user.getId(), UserAccountType.SAVINGS).getId());
        BigDecimal lienAfter = ledgerDao.getBalance(
                userAccountRepository.requireByUserIdAndType(user.getId(), UserAccountType.LIEN).getId());
        BigDecimal checkingAfter = ledgerDao.getBalance(
                userAccountRepository.requireByUserIdAndType(user.getId(), UserAccountType.CHECKING).getId());

        BigDecimal totalSeized = savingsBalance.subtract(savingsAfter)
                .add(lienBalance.subtract(lienAfter))
                .add(checkingBalance.subtract(checkingAfter));

        assertThat(totalSeized.signum()).isGreaterThanOrEqualTo(0);
        assertThat(totalSeized).isLessThanOrEqualTo(ceiling);
        assertThat(totalSeized).isLessThanOrEqualTo(groupLoss);
    }
}
