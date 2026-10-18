package com.ajoledger.backend.circle.repository;

import com.ajoledger.backend.circle.entity.CircleMemberDebitAuthorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CircleMemberDebitAuthorizationRepository extends JpaRepository<CircleMemberDebitAuthorization, UUID> {
    Optional<CircleMemberDebitAuthorization> findByCircleMembership_Id(UUID circleMembershipId);
}
