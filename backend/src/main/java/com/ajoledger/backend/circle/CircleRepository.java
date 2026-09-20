package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CircleRepository extends JpaRepository<Circle, UUID> {
    List<Circle> findByStatusAndLastRoundAtIsNotNull(CircleStatus status);
    List<Circle> findByStatusAndEnrollmentDeadlineIsNotNull(CircleStatus status);
    List<Circle> findByStatus(CircleStatus status);
}
