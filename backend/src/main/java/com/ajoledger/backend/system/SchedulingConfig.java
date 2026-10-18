package com.ajoledger.backend.system;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String EMAIL_TASK_SCHEDULER = "emailTaskScheduler";

    /** Declaring any scheduler bean switches off Boot's default, so it's redeclared under the name
     * {@code @Scheduled} falls back to. It stays single-threaded so the circle jobs keep running one at a time. */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    /** Email waits on a provider I don't control, so an outage there can't stall round transitions
     * or reconciliation, and those can't hold up a verification email. */
    @Bean(EMAIL_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler emailTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("email-");
        return scheduler;
    }
}
