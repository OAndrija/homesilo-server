package com.andrija.homesiloserver;

import com.andrija.homesiloserver.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the server against a throwaway PostgreSQL container, so local development
 * only needs Docker: {@code ./mvnw spring-boot:test-run}. Data is gone after a restart.
 */
public class TestHomesiloServerApplication {

    public static void main(String[] args) {
        SpringApplication.from(HomesiloServerApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
