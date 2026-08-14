package com.ajoledger.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthRepository extends JpaRepository<UserAuth, UUID> {

    Optional<UserAuth> findByUserId(UUID userId);

    Optional<UserAuth> findByVerificationToken(String verificationToken);
}
