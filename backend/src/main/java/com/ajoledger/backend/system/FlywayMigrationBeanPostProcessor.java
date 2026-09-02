package com.ajoledger.backend.system;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Spring Boot 4.1 ships no Flyway autoconfiguration (see LEARNING.md), so this hooks the
 * DataSource bean directly. Reading spring.datasource.* from the environment instead misses
 * Testcontainers' @ServiceConnection-provided DataSource, since that's registered as a bean,
 * not an environment property. */
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
