package com.andrija.homesiloserver.bdd;

import com.andrija.homesiloserver.support.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * The scenarios drive the whole application through its REST API (MockMvc, real
 * security chain) on a PostgreSQL container, like the integration tests.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, World.class})
public class CucumberSpringConfiguration {
}
