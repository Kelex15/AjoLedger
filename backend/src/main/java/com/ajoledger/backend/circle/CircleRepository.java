package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CircleRepository extends JpaRepository<Circle, UUID> {

}
