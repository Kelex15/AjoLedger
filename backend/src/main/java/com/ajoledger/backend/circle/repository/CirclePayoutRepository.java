package com.ajoledger.backend.circle.repository;

import com.ajoledger.backend.circle.entity.CirclePayout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CirclePayoutRepository extends JpaRepository<CirclePayout, UUID> {
    boolean existsByCircle_IdAndRecipient_Id(UUID circleId, UUID recipientId);

    List<CirclePayout> findByCircle_IdOrderByRoundNumberAsc(UUID circleId);

    @Query("SELECT p.recipient.id FROM CirclePayout p WHERE p.circle.id = :circleId")
    List<UUID> findRecipientIdsByCircle_Id(@Param("circleId") UUID circleId);
}
