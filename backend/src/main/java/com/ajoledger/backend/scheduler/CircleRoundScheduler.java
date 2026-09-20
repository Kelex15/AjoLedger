package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.circle.CircleRoundService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CircleRoundScheduler {

    private static final Logger log = LoggerFactory.getLogger(CircleRoundScheduler.class);

    private final CircleRoundService circleRoundService;

    @Scheduled(cron = "${ajoledger.scheduler.deadline-check-cron}")
    public void checkRoundDeadlines() {
        runDueRoundChecks();
    }

    void runDueRoundChecks() {
        processCircles(circleRoundService.findCirclesWithRoundsDueToday());
    }

    void processCircles(List<UUID> circleIds) {
        for (UUID circleId : circleIds) {
            try {
                circleRoundService.processRoundDeadline(circleId);
            } catch (Exception e) {
                log.error("Failed to process round deadline for circle {}", circleId, e);
            }
        }
    }
}
