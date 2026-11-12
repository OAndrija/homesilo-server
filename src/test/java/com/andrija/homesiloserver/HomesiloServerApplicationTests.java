package com.andrija.homesiloserver;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Boots the full context on PostgreSQL: Flyway migrations run and Hibernate validates
 * every entity against them. Shares the integration tests' context, so no extra database.
 */
class HomesiloServerApplicationTests extends IntegrationTest {

    @Test
    void contextLoads() {
    }
}
