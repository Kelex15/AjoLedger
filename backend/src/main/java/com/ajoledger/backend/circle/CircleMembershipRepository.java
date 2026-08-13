package com.ajoledger.backend.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CircleMembershipRepository extends JpaRepository<CircleMembership, UUID> {

}
