package com.ajoledger.backend.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ajoledger.backend.account.exception.AccountNotFoundException;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUserIdAndUserAccountType(UUID userId, UserAccountType userAccountType);

    /**
     * Unguarded lookup for internal ledger-writing code that needs to resolve accounts to build
     * legs, including system accounts (SYSTEM_BUFFER, CLEARING_*) - unlike AccountService's
     * getUserAccount, which deliberately blocks the system user id since it's the user-facing,
     * guarded read API.
     */
    default UserAccount requireByUserIdAndType(UUID userId, UserAccountType type) {
        return findByUserIdAndUserAccountType(userId, type)
                .orElseThrow(() -> new AccountNotFoundException("No " + type + " account for user " + userId));
    }
}
