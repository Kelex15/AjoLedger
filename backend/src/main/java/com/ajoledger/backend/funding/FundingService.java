package com.ajoledger.backend.funding;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.ledger.Leg;
import com.ajoledger.backend.ledger.LedgerTransaction;
import com.ajoledger.backend.ledger.LedgerService;
import com.ajoledger.backend.ledger.LedgerTransactionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FundingService {

    private final LedgerService ledgerService;
    private final UserAccountRepository userAccountRepository;

    public LedgerTransaction postDeposit(UUID userId, UserAccountType targetType, FundingType source,
                                    BigDecimal amount, String idempotencyKey) {
        UserAccount clearing = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, clearingAccountType(source));
        UserAccount target = userAccountRepository.requireByUserIdAndType(userId, targetType);
        List<Leg> legs = List.of(
                new Leg(clearing.getId(), amount, BigDecimal.ZERO),
                new Leg(target.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "DEPOSIT", LedgerTransactionType.DEPOSIT, amount,
                target.getCurrency(), "Deposit via " + source, legs);
    }

    /** Funds the demo accounts. Deliberately not a {@link FundingType}, since those are accepted from API
     * request bodies and this must only ever be reachable from the seeder. */
    public LedgerTransaction postDemoSeedDeposit(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount clearing = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.CLEARING_DEMO);
        UserAccount target = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.CHECKING);
        List<Leg> legs = List.of(
                new Leg(clearing.getId(), amount, BigDecimal.ZERO),
                new Leg(target.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "DEPOSIT", LedgerTransactionType.DEPOSIT, amount,
                target.getCurrency(), "Demo seed deposit", legs);
    }

    public LedgerTransaction postWithdrawal(UUID userId, UserAccountType sourceType, FundingType destination,
                                       BigDecimal amount, String idempotencyKey) {
        UserAccount source = userAccountRepository.requireByUserIdAndType(userId, sourceType);
        UserAccount clearing = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, clearingAccountType(destination));
        List<Leg> legs = List.of(
                new Leg(source.getId(), amount, BigDecimal.ZERO),
                new Leg(clearing.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "WITHDRAWAL", LedgerTransactionType.WITHDRAWAL, amount,
                source.getCurrency(), "Withdrawal to " + destination, legs);
    }

    private UserAccountType clearingAccountType(FundingType source) {
        return switch (source) {
            case STRIPE -> UserAccountType.CLEARING_STRIPE;
            case PAYPAL -> UserAccountType.CLEARING_PAYPAL;
        };
    }
}
