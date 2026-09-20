package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleMembershipRepository extends JpaRepository<CircleMembership, UUID> {
    boolean existsByUser_IdAndStatus(UUID userId, MembershipStatus status);
    boolean existsByCircle_IdAndUser_Id(UUID circleId, UUID userId);
    boolean existsByCircle_IdAndUser_IdAndStatus(UUID circleId, UUID userId, MembershipStatus status);
    List<CircleMembership> findByCircle_IdAndStatus(UUID circleId, MembershipStatus status);
    long countByUser_IdAndStatus(UUID userId, MembershipStatus status);
    Optional<CircleMembership> findByUser_IdAndStatus(UUID userId, MembershipStatus status);
    Optional<CircleMembership> findByCircle_IdAndUser_IdAndStatus(UUID circleId, UUID userId, MembershipStatus status);
    List<CircleMembership> findByUser_IdOrderByJoinedAtDesc(UUID userId);
}
