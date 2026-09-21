package com.ajoledger.backend.system;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

// Must run before application.yaml is parsed, so its ${...} placeholders see .env values.
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        Map<String, Object> values = new HashMap<>();
        dotenv.entries().forEach(entry -> values.put(entry.getKey(), entry.getValue()));

        environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
    }
}
