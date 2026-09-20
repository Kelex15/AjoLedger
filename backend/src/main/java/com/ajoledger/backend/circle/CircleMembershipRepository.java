package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
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

    interface UserStatusCount {
        UUID getUserId();
        MembershipStatus getStatus();
        long getCount();
    }

    /** Batched form of {@link #countByUser_IdAndStatus}, one row per (user, status) pair present. */
    @Query("SELECT m.user.id AS userId, m.status AS status, COUNT(m) AS count " +
            "FROM CircleMembership m WHERE m.user.id IN :userIds AND m.status IN :statuses " +
            "GROUP BY m.user.id, m.status")
    List<UserStatusCount> countByUser_IdInAndStatusIn(@Param("userIds") Collection<UUID> userIds,
                                                        @Param("statuses") Collection<MembershipStatus> statuses);
}
