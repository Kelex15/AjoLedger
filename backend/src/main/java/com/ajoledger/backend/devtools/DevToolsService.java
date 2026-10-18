package com.ajoledger.backend.devtools;

import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.exception.CircleNotFoundException;
import com.ajoledger.backend.circle.repository.CircleRepository;
import com.ajoledger.backend.scheduler.CircleEnrollmentScheduler;
import com.ajoledger.backend.scheduler.CircleRoundScheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Lets a circle skip ahead to its next time-gated transition without waiting for the real
 * scheduler cron. Only registered under the "dev" profile, never wired up in prod. */
@Profile("dev")
@Service
@RequiredArgsConstructor
public class DevToolsService {

    private final CircleRepository circleRepository;
    private final CircleRoundScheduler circleRoundScheduler;
    private final CircleEnrollmentScheduler circleEnrollmentScheduler;

    /** Backdates lastRoundAt past this circle's term, then runs the real scheduler pass so it
     * gets discovered and processed exactly as it would be in prod. */
    public void forceRound(UUID circleId) {
        Circle circle = requireCircle(circleId);
        circle.setLastRoundAt(Instant.now().minus(Duration.ofDays((circle.getTerm() + 1L) * 7)));
        circleRepository.save(circle);
        circleRoundScheduler.checkRoundDeadlines();
    }

    /** Backdates enrollmentDeadline into the past, then runs the real scheduler pass so the
     * circle gets discovered and closed exactly as it would be in prod. */
    public void forceEnrollmentClose(UUID circleId) {
        Circle circle = requireCircle(circleId);
        circle.setEnrollmentDeadline(Instant.now());
        circleRepository.save(circle);
        circleEnrollmentScheduler.checkEnrollmentDeadlines();
    }

    private Circle requireCircle(UUID circleId) {
        return circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
    }
}
