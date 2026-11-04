package com.andrija.homesiloserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HomesiloServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomesiloServerApplication.class, args);
    }
}
