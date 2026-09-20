package com.ajoledger.backend.account.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUserIdAndUserAccountType(UUID userId, UserAccountType userAccountType);

    List<UserAccount> findByUserId(UUID userId);

    List<UserAccount> findByUserIdInAndUserAccountType(Collection<UUID> userIds, UserAccountType userAccountType);

    /** Unguarded, unlike AccountService's getUserAccount. Ledger-writing code legitimately needs
     * to resolve system accounts (SYSTEM_BUFFER, CLEARING_*) to build legs. */
    default UserAccount requireByUserIdAndType(UUID userId, UserAccountType type) {
        return findByUserIdAndUserAccountType(userId, type)
                .orElseThrow(() -> new AccountNotFoundException("No " + type + " account for user " + userId));
    }
}
