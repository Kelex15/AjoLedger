package com.ajoledger.backend.system;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Spring Boot 4.1 doesn't autoconfigure Flyway, so this hooks the DataSource bean directly
 * instead. Reading spring.datasource.* from the environment wouldn't work here, since
 * Testcontainers registers its @ServiceConnection DataSource as a bean, not a property. */
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
