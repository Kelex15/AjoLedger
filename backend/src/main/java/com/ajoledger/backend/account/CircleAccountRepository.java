package com.ajoledger.backend.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ajoledger.backend.account.exception.AccountNotFoundException;

public interface CircleAccountRepository extends JpaRepository<CircleAccount, UUID> {
    Optional<CircleAccount> findByCircleId(UUID circleId);

    default CircleAccount requireByCircleId(UUID circleId) {
        return findByCircleId(circleId)
                .orElseThrow(() -> new AccountNotFoundException("No pot account for circle " + circleId));
    }
}
