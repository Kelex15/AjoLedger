package com.ajoledger.backend.circle.policy;

import org.springframework.stereotype.Component;

import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.repository.CircleInviteRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;

@Component("FIXED_SIZE")
public class FixedSizeCircleSizingPolicy extends CircleSizingPolicy {

    public FixedSizeCircleSizingPolicy(CircleInviteRepository circleInviteRepository,
                                        CircleMembershipRepository circleMembershipRepository) {
        super(circleInviteRepository, circleMembershipRepository);
    }

    @Override
    protected boolean hasEnrollmentRoom(Circle circle) {
        return isBelowCap(circle);
    }
}
