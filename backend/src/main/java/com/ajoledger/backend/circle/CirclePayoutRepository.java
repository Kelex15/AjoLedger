package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CirclePayoutRepository extends JpaRepository<CirclePayout, UUID> {
    boolean existsByCircle_IdAndRecipient_Id(UUID circleId, UUID recipientId);

    @Query("SELECT p.recipient.id FROM CirclePayout p WHERE p.circle.id = :circleId")
    List<UUID> findRecipientIdsByCircle_Id(@Param("circleId") UUID circleId);
}
