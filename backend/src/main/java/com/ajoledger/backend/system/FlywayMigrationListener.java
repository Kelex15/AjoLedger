package com.ajoledger.backend.system;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

public class FlywayMigrationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        Flyway.configure()
                .dataSource(
                        environment.getProperty("spring.datasource.url"),
                        environment.getProperty("spring.datasource.username"),
                        environment.getProperty("spring.datasource.password"))
                .load()
                .migrate();
    }
}
