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

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccount;
import com.ajoledger.backend.account.UserAccountType;
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
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerDao ledgerDao;

    private User createUser() {
        User user = User.builder()
                .username("user" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("User")
                .email("test-" + UUID.randomUUID() + "@example.com")
                .build();
        return userRepository.save(user);
    }

    private UserAccount createFundedAccount(User user, UserAccountType type, BigDecimal balance) {
        UserAccount account = accountService.createUserAccount(user, type);
        jdbcTemplate.update("UPDATE account SET balance = ? WHERE id = ?", balance, account.getId());
        return account;
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
        assertThat(ledgerDao.getBalance(payerSavings.getId())).isEqualByComparingTo("0.00");
        assertThat(ledgerDao.getBalance(recipientSavings.getId())).isEqualByComparingTo("100.00");
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
        assertThat(ledgerDao.getBalance(payerSavings.getId())).isEqualByComparingTo("20.00");
        assertThat(ledgerDao.getBalance(recipientSavings.getId())).isEqualByComparingTo("30.00");
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

        assertThatThrownBy(() -> transactionService.postTransfer(payer.getId(), UserAccountType.SAVINGS,
                recipient.getId(), UserAccountType.SAVINGS, new BigDecimal("50.00"), UUID.randomUUID().toString()))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(ledgerDao.getBalance(payerSavings.getId())).isEqualByComparingTo("10.00");
        Long transactionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction", Long.class);
        assertThat(transactionCount).isZero();
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
