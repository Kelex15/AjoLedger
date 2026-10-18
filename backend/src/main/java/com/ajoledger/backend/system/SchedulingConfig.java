package com.ajoledger.backend.system;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String EMAIL_TASK_SCHEDULER = "emailTaskScheduler";
    public static final String RECONCILIATION_TASK_SCHEDULER = "reconciliationTaskScheduler";

    /** Declaring any scheduler bean switches off Boot's default, so it's redeclared under the name
     * {@code @Scheduled} falls back to. It stays single-threaded so the circle jobs keep running one at a time. */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    /** Email and reconciliation each wait on providers I don't control, so they get their own threads.
     * An outage at Resend, Stripe or PayPal can't stall round transitions or each other. */
    @Bean(EMAIL_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler emailTaskScheduler() {
        return singleThreadScheduler("email-");
    }

    @Bean(RECONCILIATION_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler reconciliationTaskScheduler() {
        return singleThreadScheduler("reconciliation-");
    }

    private static ThreadPoolTaskScheduler singleThreadScheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        return scheduler;
    }
}
