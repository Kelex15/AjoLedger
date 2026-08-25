package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CirclePayoutRepository extends JpaRepository<CirclePayout, UUID> {
    boolean existsByCircle_IdAndRecipient_Id(UUID circleId, UUID recipientId);
}
