package com.ajoledger.backend.funding;

import com.ajoledger.backend.account.UserAccount;
import com.ajoledger.backend.account.UserAccountRepository;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.transaction.Leg;
import com.ajoledger.backend.transaction.Transaction;
import com.ajoledger.backend.transaction.TransactionService;
import com.ajoledger.backend.transaction.TransactionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FundingService {

    private final TransactionService transactionService;
    private final UserAccountRepository userAccountRepository;

    public Transaction postDeposit(UUID userId, UserAccountType targetType, FundingType source,
                                    BigDecimal amount, String idempotencyKey) {
        UserAccount clearing = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, clearingAccountType(source));
        UserAccount target = userAccountRepository.requireByUserIdAndType(userId, targetType);
        List<Leg> legs = List.of(
                new Leg(clearing.getId(), amount, BigDecimal.ZERO),
                new Leg(target.getId(), BigDecimal.ZERO, amount));
        return transactionService.postLedgerTransaction(idempotencyKey, "DEPOSIT", TransactionType.DEPOSIT, amount,
                "Deposit via " + source, legs);
    }

    public Transaction postWithdrawal(UUID userId, UserAccountType sourceType, FundingType destination,
                                       BigDecimal amount, String idempotencyKey) {
        UserAccount source = userAccountRepository.requireByUserIdAndType(userId, sourceType);
        UserAccount clearing = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, clearingAccountType(destination));
        List<Leg> legs = List.of(
                new Leg(source.getId(), amount, BigDecimal.ZERO),
                new Leg(clearing.getId(), BigDecimal.ZERO, amount));
        return transactionService.postLedgerTransaction(idempotencyKey, "WITHDRAWAL", TransactionType.WITHDRAWAL, amount,
                "Withdrawal to " + destination, legs);
    }

    private UserAccountType clearingAccountType(FundingType source) {
        return switch (source) {
            case STRIPE -> UserAccountType.CLEARING_STRIPE;
            case PAYPAL -> UserAccountType.CLEARING_PAYPAL;
        };
    }
}
