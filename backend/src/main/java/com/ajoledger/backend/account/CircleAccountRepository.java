package com.ajoledger.backend.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleAccountRepository extends JpaRepository<CircleAccount, UUID> {
    Optional<CircleAccount> findByCircleId(UUID circleId);
}
