package com.ajoledger.backend.system;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs Flyway migrations by hooking the DataSource bean directly, regardless of how it was
 * configured (application.yaml properties, .env, or a Testcontainers @ServiceConnection bean) -
 * Spring Boot 4.1 ships no Flyway autoconfiguration (see LEARNING.md), and reading
 * spring.datasource.* from the environment at ApplicationEnvironmentPreparedEvent time (the
 * first approach here) doesn't see @ServiceConnection-provided connection details, since those
 * are registered as a bean, not an environment property, and only after that event fires.
 * postProcessAfterInitialization is the last step of the DataSource bean's own creation, so this
 * always runs before anything (e.g. Hibernate's EntityManagerFactory) can consume the DataSource.
 */
@Component
public class FlywayMigrationBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
            Flyway.configure().dataSource(dataSource).load().migrate();
        }
        return bean;
    }
}
