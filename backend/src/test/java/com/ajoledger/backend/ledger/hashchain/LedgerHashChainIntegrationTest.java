package com.ajoledger.backend.ledger.hashchain;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.ledger.model.Leg;
import com.ajoledger.backend.ledger.LedgerService;
import com.ajoledger.backend.ledger.LedgerTransactionType;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
// The tamper test permanently corrupts a row as part of what it's testing, and verifyChain()
// walks the whole shared chain from genesis, so it must run after the clean-verification test.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerHashChainIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private FundingService fundingService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private LedgerHashChainSealingService sealingService;
    @Autowired
    private LedgerHashChainVerificationService verificationService;

    private User createUser() {
        User user = User.builder()
                .username("user" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("User")
                .email("test-" + UUID.randomUUID() + "@example.com")
                .build();
        return userRepository.save(user);
    }

    private UserAccount createFundedAccount(User user, BigDecimal balance) {
        UserAccount account = accountService.createUserAccount(user, UserAccountType.CHECKING);
        if (balance.signum() > 0) {
            fundingService.postDeposit(user.getId(), UserAccountType.CHECKING, FundingType.STRIPE, balance, UUID.randomUUID().toString());
        }
        return account;
    }

    @Test
    @Order(1)
    void sealsEveryPendingRowExactlyOnceAndVerifiesClean() {
        User fromUser = createUser();
        User toUser = createUser();
        UserAccount from = createFundedAccount(fromUser, new BigDecimal("100.00"));
        UserAccount to = createFundedAccount(toUser, new BigDecimal("0.00"));

        sealingService.sealPendingRows();

        List<Leg> legs = List.of(
                new Leg(from.getId(), new BigDecimal("20.00"), BigDecimal.ZERO),
                new Leg(to.getId(), BigDecimal.ZERO, new BigDecimal("20.00")));
        ledgerService.postLedgerOperation("TEST_OP", fromUser.getId(), operationId ->
                ledgerService.postLedgerTransaction(UUID.randomUUID().toString(), "TEST", LedgerTransactionType.TRANSFER,
                        new BigDecimal("20.00"), Currency.USD, "test transfer", legs, operationId));

        int sealed = sealingService.sealPendingRows();

        assertThat(sealed).isEqualTo(4);
        assertThat(sealingService.sealPendingRows()).isZero();

        LedgerHashChainVerificationService.VerificationResult result = verificationService.verifyChain();
        assertThat(result).isInstanceOf(LedgerHashChainVerificationService.VerificationResult.Clean.class);
    }

    @Test
    @Order(2)
    void detectsAnAmountChangedAfterSealingEvenWithTheAppendOnlyTriggerDisabled() {
        User fromUser = createUser();
        User toUser = createUser();
        UserAccount from = createFundedAccount(fromUser, new BigDecimal("100.00"));
        UserAccount to = createFundedAccount(toUser, new BigDecimal("0.00"));

        sealingService.sealPendingRows();

        List<Leg> legs = List.of(
                new Leg(from.getId(), new BigDecimal("20.00"), BigDecimal.ZERO),
                new Leg(to.getId(), BigDecimal.ZERO, new BigDecimal("20.00")));
        var transaction = ledgerService.postLedgerTransaction(UUID.randomUUID().toString(), "TEST", LedgerTransactionType.TRANSFER,
                new BigDecimal("20.00"), Currency.USD, "test transfer", legs);
        sealingService.sealPendingRows();

        UUID tamperedEntryId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entry WHERE transaction_id = ? AND debit_amount > 0",
                UUID.class, transaction.id());

        jdbcTemplate.execute("ALTER TABLE ledger_entry DISABLE TRIGGER trg_ledger_entry_append_only");
        jdbcTemplate.update("UPDATE ledger_entry SET debit_amount = debit_amount + 1 WHERE id = ?", tamperedEntryId);
        jdbcTemplate.execute("ALTER TABLE ledger_entry ENABLE TRIGGER trg_ledger_entry_append_only");

        LedgerHashChainVerificationService.VerificationResult result = verificationService.verifyChain();

        assertThat(result).isInstanceOf(LedgerHashChainVerificationService.VerificationResult.Broken.class);
    }
}
