package com.ajoledger.backend.circle.policy;

import org.springframework.stereotype.Component;

import java.time.Instant;

import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.repository.CircleInviteRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;

@Component("OPEN_ENROLLMENT")
public class OpenEnrollmentCircleSizingPolicy extends CircleSizingPolicy {

    public OpenEnrollmentCircleSizingPolicy(CircleInviteRepository circleInviteRepository,
                                             CircleMembershipRepository circleMembershipRepository) {
        super(circleInviteRepository, circleMembershipRepository);
    }

    @Override
    protected boolean hasEnrollmentRoom(Circle circle) {
        return isBelowCap(circle) && !Instant.now().isAfter(circle.getEnrollmentDeadline());
    }
}
