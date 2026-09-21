package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CircleMemberDebitAuthorizationRepository extends JpaRepository<CircleMemberDebitAuthorization, UUID> {
    Optional<CircleMemberDebitAuthorization> findByCircleMembership_Id(UUID circleMembershipId);
}
