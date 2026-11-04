package com.andrija.homesiloserver;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade path for databases created by the pre-Flyway version: schema already
 * present (no history table), users all with role USER.
 */
class MigrationTest {

    @Test
    void legacyDatabaseIsBaselinedUpgradedAndGetsAnAdmin() {
        SingleConnectionDataSource dataSource = dataSource("legacy");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // What Hibernate's ddl-auto=update produced before this change (no Flyway history).
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        jdbc.execute("DROP TABLE \"flyway_schema_history\"");
        insertUser(jdbc, "first", "USER", "2025-01-01");
        insertUser(jdbc, "second", "USER", "2025-06-01");

        Flyway.configure().dataSource(dataSource).baselineOnMigrate(true).baselineVersion("0").load().migrate();

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'first'", String.class)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'second'", String.class)).isEqualTo("USER");
        // V2 dropped the one-entry-per-blob constraint, so two entries may share a blob.
        Integer constraints = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_FILE_METADATA_OWNER_STORED_FILENAME'",
                Integer.class);
        assertThat(constraints).isZero();
    }

    @Test
    void existingAdminIsLeftAlone() {
        SingleConnectionDataSource dataSource = dataSource("fresh");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        insertUser(jdbc, "old", "USER", "2025-01-01");
        insertUser(jdbc, "boss", "ADMIN", "2025-06-01");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'old'", String.class)).isEqualTo("USER");
    }

    /** One shared connection: H2 ties check constraints to the session that created them. */
    private static SingleConnectionDataSource dataSource(String name) {
        return new SingleConnectionDataSource(
                "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
    }

    private static void insertUser(JdbcTemplate jdbc, String username, String role, String createdOn) {
        jdbc.update("INSERT INTO users (id, username, password, email, created_at, role, enabled, storage_quota_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), username, "x", username + "@example.com",
                LocalDate.parse(createdOn).atStartOfDay(), role, true, 1L);
    }
}
