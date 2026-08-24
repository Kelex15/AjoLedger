package com.ajoledger.backend.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUserIdAndUserAccountType(UUID userId, UserAccountType userAccountType);
}
