package com.ajoledger.backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;

public interface CircleAccountRepository extends JpaRepository<CircleAccount, UUID> {
    Optional<CircleAccount> findByCircleIdAndCircleAccountType(UUID circleId, CircleAccountType circleAccountType);

    default CircleAccount requireByCircleIdAndType(UUID circleId, CircleAccountType type) {
        return findByCircleIdAndCircleAccountType(circleId, type)
                .orElseThrow(() -> new AccountNotFoundException("No " + type + " account for circle " + circleId));
    }
}
