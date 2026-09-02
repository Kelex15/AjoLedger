package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.circle.CircleService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CircleEnrollmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(CircleEnrollmentScheduler.class);

    private final CircleService circleService;

    @Scheduled(cron = "${ajoledger.scheduler.enrollment-close-cron}")
    public void checkEnrollmentDeadlines() {
        runEnrollmentChecks();
    }

    void runEnrollmentChecks() {
        processCircles(circleService.findCirclesWithClosedEnrollment());
    }

    void processCircles(List<UUID> circleIds) {
        for (UUID circleId : circleIds) {
            try {
                circleService.closeEnrollment(circleId);
            } catch (Exception e) {
                log.error("Failed to close enrollment for circle {}", circleId, e);
            }
        }
    }
}
