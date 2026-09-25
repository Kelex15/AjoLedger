package com.ajoledger.backend.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.AccountRepository;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.ledger.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.ledger.exception.InvalidTransferTargetException;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;
import com.ajoledger.backend.user.exception.UserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private FundingService fundingService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerDao ledgerDao;
    @Autowired
    private AccountRepository accountRepository;

    private User createUser() {
        User user = User.builder()
                .username("user" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("User")
                .email("test-" + UUID.randomUUID() + "@example.com")
                .build();
        return userRepository.save(user);
    }

    /** Funds an account via a real deposit, so the cached balance and the ledger's own aggregate agree from the start. */
    private UserAccount createFundedAccount(User user, UserAccountType type, BigDecimal balance) {
        UserAccount account = accountService.createUserAccount(user, type);
        fundingService.postDeposit(user.getId(), type, FundingType.STRIPE, balance, UUID.randomUUID().toString());
        return account;
    }

    /** Asserts the cached Account.balance and the ledger's own aggregate agree, both equal to expected. */
    private void assertBalance(UUID accountId, String expected) {
        BigDecimal cached = accountRepository.findById(accountId).orElseThrow().getBalance();
        assertThat(cached).isEqualByComparingTo(expected);
        assertThat(ledgerDao.getBalance(accountId)).isEqualByComparingTo(expected);
    }

    @Test
    void concurrentTransfersFromSameAccountDoNotDoubleSpend() throws Exception {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("100.00"));
        UserAccount recipientSavings = accountService.createUserAccount(recipient, UserAccountType.SAVINGS);

        // Two concurrent transfers of the full balance, only one should succeed.
        Callable<LedgerTransaction> attempt = () -> ledgerService.postTransfer(
                payer.getId(), UserAccountType.SAVINGS, recipient.getId(), UserAccountType.SAVINGS,
                new BigDecimal("100.00"), UUID.randomUUID().toString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<LedgerTransaction> synchronizedAttempt = () -> {
            ready.countDown();
            go.await();
            return attempt.call();
        };

        Future<LedgerTransaction> f1 = executor.submit(synchronizedAttempt);
        Future<LedgerTransaction> f2 = executor.submit(synchronizedAttempt);
        ready.await();
        go.countDown();

        int succeeded = 0;
        int failed = 0;
        for (Future<LedgerTransaction> f : List.of(f1, f2)) {
            try {
                f.get();
                succeeded++;
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientBalanceException.class);
                failed++;
            }
        }
        executor.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(failed).isEqualTo(1);
        assertBalance(payerSavings.getId(), "0.00");
        assertBalance(recipientSavings.getId(), "100.00");
    }

    @Test
    void idempotencyKeyReplayReturnsCachedResultWithoutReprocessing() {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("50.00"));
        UserAccount recipientSavings = accountService.createUserAccount(recipient, UserAccountType.SAVINGS);
        String idempotencyKey = UUID.randomUUID().toString();

        LedgerTransaction first = ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("30.00"), idempotencyKey);
        LedgerTransaction second = ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("30.00"), idempotencyKey);

        assertThat(second.id()).isEqualTo(first.id());
        // balance reflects exactly one transfer, not two
        assertBalance(payerSavings.getId(), "20.00");
        assertBalance(recipientSavings.getId(), "30.00");
    }

    @Test
    void idempotencyKeyReusedWithDifferentRequestIsRejected() {
        User payer = createUser();
        User recipient = createUser();
        createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("100.00"));
        accountService.createUserAccount(recipient, UserAccountType.SAVINGS);
        String idempotencyKey = UUID.randomUUID().toString();

        ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("10.00"), idempotencyKey);

        assertThatThrownBy(() -> ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("20.00"), idempotencyKey))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void transferLegsResolveToTheCorrectFromAndToAccounts() {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("50.00"));
        UserAccount recipientSavings = accountService.createUserAccount(recipient, UserAccountType.SAVINGS);

        LedgerTransaction transaction = ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("10.00"), UUID.randomUUID().toString());

        List<LedgerEntry> legs = ledgerDao.findLegsByTransactionId(transaction.id());
        assertThat(legs).hasSize(2);

        LedgerEntry debitLeg = legs.stream().filter(leg -> leg.debitAmount().signum() > 0).findFirst().orElseThrow();
        LedgerEntry creditLeg = legs.stream().filter(leg -> leg.creditAmount().signum() > 0).findFirst().orElseThrow();
        assertThat(debitLeg.accountId()).isEqualTo(payerSavings.getId());
        assertThat(creditLeg.accountId()).isEqualTo(recipientSavings.getId());
    }

    @Test
    void transferToSelfIsNotRestrictedToCheckingTheWayTransfersToOthersAre() {
        User user = createUser();
        UserAccount checking = createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("50.00"));
        UserAccount savings = accountService.createUserAccount(user, UserAccountType.SAVINGS);

        ledgerService.postTransferToUser(user.getId(), UserAccountType.CHECKING, user.getUsername(),
                UserAccountType.SAVINGS, new BigDecimal("20.00"), UUID.randomUUID().toString());

        assertBalance(checking.getId(), "30.00");
        assertBalance(savings.getId(), "20.00");
    }

    @Test
    void transferToAnotherUserRejectsNonCheckingDestination() {
        User payer = createUser();
        User recipient = createUser();
        createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("50.00"));
        accountService.createUserAccount(recipient, UserAccountType.SAVINGS);

        assertThatThrownBy(() -> ledgerService.postTransferToUser(payer.getId(), UserAccountType.SAVINGS,
                recipient.getUsername(), UserAccountType.SAVINGS, new BigDecimal("10.00"), UUID.randomUUID().toString()))
                .isInstanceOf(InvalidTransferTargetException.class);
    }

    @Test
    void transferToAnotherUserAllowsCheckingDestination() {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("50.00"));
        UserAccount recipientChecking = accountService.createUserAccount(recipient, UserAccountType.CHECKING);

        ledgerService.postTransferToUser(payer.getId(), UserAccountType.SAVINGS, recipient.getUsername(),
                UserAccountType.CHECKING, new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertBalance(payerSavings.getId(), "40.00");
        assertBalance(recipientChecking.getId(), "10.00");
    }

    @Test
    void transferToUnknownUsernameThrowsUserNotFound() {
        User payer = createUser();
        createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("50.00"));

        assertThatThrownBy(() -> ledgerService.postTransferToUser(payer.getId(), UserAccountType.SAVINGS,
                "no-such-user", UserAccountType.CHECKING, new BigDecimal("10.00"), UUID.randomUUID().toString()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void insufficientBalanceLeavesLedgerUntouched() {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("10.00"));
        accountService.createUserAccount(recipient, UserAccountType.SAVINGS);
        Long transactionCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction", Long.class);

        assertThatThrownBy(() -> ledgerService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("50.00"), UUID.randomUUID().toString()))
                .isInstanceOf(InsufficientBalanceException.class);

        assertBalance(payerSavings.getId(), "10.00");
        Long transactionCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction", Long.class);
        assertThat(transactionCountAfter).isEqualTo(transactionCountBefore);
    }

    @Test
    void unbalancedLedgerEntryIsRejectedAtCommit() {
        User user = createUser();
        UserAccount account = createFundedAccount(user, UserAccountType.SAVINGS, new BigDecimal("100.00"));
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            jdbcTemplate.update(
                    "INSERT INTO transaction (id, amount, currency, description, transaction_type, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    transactionId, new BigDecimal("100.00"), "USD", "unbalanced test", "TRANSFER", Timestamp.from(Instant.now()));
            // debit 100, credit only 50: does not net to zero
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), new BigDecimal("100.00"), BigDecimal.ZERO, transactionId, Timestamp.from(Instant.now()));
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), BigDecimal.ZERO, new BigDecimal("50.00"), transactionId, Timestamp.from(Instant.now()));
            return null;
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void ledgerTablesRejectUpdateAndDelete() {
        User user = createUser();
        UserAccount account = createFundedAccount(user, UserAccountType.SAVINGS, new BigDecimal("100.00"));
        UUID transactionId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            jdbcTemplate.update(
                    "INSERT INTO transaction (id, amount, currency, description, transaction_type, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    transactionId, new BigDecimal("10.00"), "USD", "append-only test", "TRANSFER", Timestamp.from(Instant.now()));
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), new BigDecimal("10.00"), BigDecimal.ZERO, transactionId, Timestamp.from(Instant.now()));
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), BigDecimal.ZERO, new BigDecimal("10.00"), transactionId, Timestamp.from(Instant.now()));
            return null;
        });

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE transaction SET description = 'changed' WHERE id = ?", transactionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM transaction WHERE id = ?", transactionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entry SET debit_amount = 999 WHERE transaction_id = ?", transactionId))
                .isInstanceOf(DataAccessException.class);
    }
    @Test
    void statementOnlyContainsTheUsersOwnEntries() {
        User owner = createUser();
        User stranger = createUser();
        UserAccount ownerChecking = createFundedAccount(owner, UserAccountType.CHECKING, new BigDecimal("100.00"));
        createFundedAccount(stranger, UserAccountType.CHECKING, new BigDecimal("50.00"));

        List<LedgerActivity> statement = ledgerService.findActivityForUser(owner.getId(), null, null, null, 50);

        assertThat(statement).isNotEmpty();
        assertThat(statement).allSatisfy(entry ->
                assertThat(entry.accountNumber()).isEqualTo(ownerChecking.getAccountNumber()));
    }

    @Test
    void transferIsNegativeOnTheSourceAndPositiveOnTheDestination() {
        User user = createUser();
        UserAccount checking = createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("100.00"));
        UserAccount savings = accountService.createUserAccount(user, UserAccountType.SAVINGS);

        ledgerService.postTransfer(user.getId(), UserAccountType.CHECKING, user.getId(), UserAccountType.SAVINGS,
                new BigDecimal("40.00"), UUID.randomUUID().toString());

        List<LedgerActivity> statement = ledgerService.findActivityForUser(user.getId(), null, null, null, 50);
        List<LedgerActivity> transferLegs = statement.stream()
                .filter(entry -> entry.transactionType() == LedgerTransactionType.TRANSFER)
                .toList();

        assertThat(transferLegs).hasSize(2);
        assertThat(transferLegs).filteredOn(entry -> entry.accountNumber().equals(checking.getAccountNumber()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("-40.00"));
        assertThat(transferLegs).filteredOn(entry -> entry.accountNumber().equals(savings.getAccountNumber()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("40.00"));
    }

    @Test
    void accountNumberNarrowsTheStatementAndCannotReachAnotherUsersAccount() {
        User user = createUser();
        User stranger = createUser();
        UserAccount checking = createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("100.00"));
        createFundedAccount(user, UserAccountType.SAVINGS, new BigDecimal("25.00"));
        UserAccount strangerChecking = createFundedAccount(stranger, UserAccountType.CHECKING, new BigDecimal("70.00"));

        List<LedgerActivity> ownAccount = ledgerService.findActivityForUser(
                user.getId(), checking.getAccountNumber(), null, null, 50);
        assertThat(ownAccount).isNotEmpty();
        assertThat(ownAccount).allSatisfy(entry ->
                assertThat(entry.accountType()).isEqualTo(UserAccountType.CHECKING));

        List<LedgerActivity> someoneElses = ledgerService.findActivityForUser(
                user.getId(), strangerChecking.getAccountNumber(), null, null, 50);
        assertThat(someoneElses).isEmpty();
    }

    @Test
    void cursorPaginationWalksEveryEntryExactlyOnce() {
        User user = createUser();
        UserAccount checking = createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("10.00"));
        for (int i = 0; i < 4; i++) {
            fundingService.postDeposit(user.getId(), UserAccountType.CHECKING, FundingType.STRIPE,
                    new BigDecimal("10.00"), UUID.randomUUID().toString());
        }

        List<LedgerActivity> all = ledgerService.findActivityForUser(
                user.getId(), checking.getAccountNumber(), null, null, 50);
        assertThat(all).hasSize(5);

        List<UUID> paged = new ArrayList<>();
        Instant cursorCreatedAt = null;
        UUID cursorEntryId = null;
        for (int page = 0; page < 5; page++) {
            List<LedgerActivity> rows = ledgerService.findActivityForUser(
                    user.getId(), checking.getAccountNumber(), cursorCreatedAt, cursorEntryId, 2);
            if (rows.isEmpty()) {
                break;
            }
            rows.forEach(entry -> paged.add(entry.entryId()));
            LedgerActivity last = rows.getLast();
            cursorCreatedAt = last.createdAt();
            cursorEntryId = last.entryId();
        }

        assertThat(paged).doesNotHaveDuplicates();
        assertThat(paged).containsExactlyElementsOf(all.stream().map(LedgerActivity::entryId).toList());
    }
    @Test
    void transferCannotMoveCollateralOutOfLien() {
        User user = createUser();
        createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("100.00"));
        createFundedAccount(user, UserAccountType.LIEN, new BigDecimal("100.00"));
        accountService.createUserAccount(user, UserAccountType.SAVINGS);

        assertThatThrownBy(() -> ledgerService.postTransferToUser(user.getId(), UserAccountType.LIEN,
                user.getUsername(), UserAccountType.SAVINGS, new BigDecimal("50.00"), UUID.randomUUID().toString()))
                .isInstanceOf(InvalidTransferTargetException.class);
    }

    @Test
    void transferCanPayIntoLienSincePayingInIsUnrestricted() {
        User user = createUser();
        UserAccount checking = createFundedAccount(user, UserAccountType.CHECKING, new BigDecimal("100.00"));
        UserAccount lien = accountService.createUserAccount(user, UserAccountType.LIEN);

        ledgerService.postTransferToUser(user.getId(), UserAccountType.CHECKING, user.getUsername(),
                UserAccountType.LIEN, new BigDecimal("50.00"), UUID.randomUUID().toString());

        assertBalance(checking.getId(), "50.00");
        assertBalance(lien.getId(), "50.00");
    }
}
