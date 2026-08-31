package com.ajoledger.backend.account;

import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.account.repository.CircleAccountRepository;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountRepository userAccountRepository;
    private final CircleAccountRepository circleAccountRepository;

    public UserAccount createUserAccount(User user, UserAccountType type) {
        UserAccount account = UserAccount.builder()
                .accountNumber(generateAccountNumber())
                .currency(Currency.USD)
                .balance(BigDecimal.ZERO)
                .user(user)
                .userAccountType(type)
                .build();
        return userAccountRepository.save(account);
    }

    public CircleAccount createCircleAccount(Circle circle) {
        CircleAccount account = CircleAccount.builder()
                .accountNumber(generateAccountNumber())
                .currency(Currency.USD)
                .balance(BigDecimal.ZERO)
                .circle(circle)
                .build();
        return circleAccountRepository.save(account);
    }

    public Optional<UserAccount> getUserAccount(UUID userId, UserAccountType type) {
        if (Constants.Account.SYSTEM_USER_ID.equals(userId)) {
            throw new AccountNotFoundException("No such account");
        }
        return userAccountRepository.findByUserIdAndUserAccountType(userId, type);
    }

    public Optional<CircleAccount> getCircleAccount(UUID circleId) {
        return circleAccountRepository.findByCircleId(circleId);
    }

    private String generateAccountNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
