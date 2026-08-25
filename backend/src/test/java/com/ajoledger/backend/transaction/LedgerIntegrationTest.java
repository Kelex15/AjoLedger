package com.ajoledger.backend.transaction;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ajoledger.backend.account.AccountRepository;
import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccount;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.ledger.LedgerDao;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.transaction.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TransactionService transactionService;
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

        // Two concurrent transfers of the full balance - only one should succeed.
        Callable<Transaction> attempt = () -> transactionService.postTransfer(
                payer.getId(), UserAccountType.SAVINGS, recipient.getId(), UserAccountType.SAVINGS,
                new BigDecimal("100.00"), UUID.randomUUID().toString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Transaction> synchronizedAttempt = () -> {
            ready.countDown();
            go.await();
            return attempt.call();
        };

        Future<Transaction> f1 = executor.submit(synchronizedAttempt);
        Future<Transaction> f2 = executor.submit(synchronizedAttempt);
        ready.await();
        go.countDown();

        int succeeded = 0;
        int failed = 0;
        for (Future<Transaction> f : List.of(f1, f2)) {
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

        Transaction first = transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("30.00"), idempotencyKey);
        Transaction second = transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
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

        transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("10.00"), idempotencyKey);

        assertThatThrownBy(() -> transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("20.00"), idempotencyKey))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void insufficientBalanceLeavesLedgerUntouched() {
        User payer = createUser();
        User recipient = createUser();
        UserAccount payerSavings = createFundedAccount(payer, UserAccountType.SAVINGS, new BigDecimal("10.00"));
        accountService.createUserAccount(recipient, UserAccountType.SAVINGS);
        Long transactionCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction", Long.class);

        assertThatThrownBy(() -> transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
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
                    "INSERT INTO transaction (id, amount, description, transaction_type, created_at) VALUES (?, ?, ?, ?, ?)",
                    transactionId, new BigDecimal("100.00"), "unbalanced test", "TRANSFER", LocalDateTime.now());
            // debit 100, credit only 50 - does not net to zero
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), new BigDecimal("100.00"), BigDecimal.ZERO, transactionId, LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), BigDecimal.ZERO, new BigDecimal("50.00"), transactionId, LocalDateTime.now());
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
                    "INSERT INTO transaction (id, amount, description, transaction_type, created_at) VALUES (?, ?, ?, ?, ?)",
                    transactionId, new BigDecimal("10.00"), "append-only test", "TRANSFER", LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), new BigDecimal("10.00"), BigDecimal.ZERO, transactionId, LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO ledger_entry (id, account_id, debit_amount, credit_amount, transaction_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), account.getId(), BigDecimal.ZERO, new BigDecimal("10.00"), transactionId, LocalDateTime.now());
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
}
