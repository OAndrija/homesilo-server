package com.andrija.homesiloserver;

import com.andrija.homesiloserver.support.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade path for databases created by the pre-Flyway version: schema already
 * present (no history table), users all with role USER. Each test gets its own empty
 * database in one PostgreSQL container, so migrations always start from scratch.
 */
@Testcontainers
class MigrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE);

    private SingleConnectionDataSource dataSource;

    @AfterEach
    void closeConnection() {
        if (dataSource != null) dataSource.destroy();
    }

    @Test
    void legacyDatabaseIsBaselinedUpgradedAndGetsAnAdmin() {
        dataSource = freshDatabase();
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
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = 'uq_file_metadata_owner_stored_filename'",
                Integer.class);
        assertThat(constraints).isZero();
    }

    @Test
    void existingAdminIsLeftAlone() {
        dataSource = freshDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        insertUser(jdbc, "old", "USER", "2025-01-01");
        insertUser(jdbc, "boss", "ADMIN", "2025-06-01");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'old'", String.class)).isEqualTo("USER");
    }

    /** Creates an empty database in the container and connects to it. */
    private static SingleConnectionDataSource freshDatabase() {
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        SingleConnectionDataSource admin = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        try {
            new JdbcTemplate(admin).execute("CREATE DATABASE " + name);
        } finally {
            admin.destroy();
        }
        String url = "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(), postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), name);
        return new SingleConnectionDataSource(url, postgres.getUsername(), postgres.getPassword(), true);
    }

    private static void insertUser(JdbcTemplate jdbc, String username, String role, String createdOn) {
        jdbc.update("INSERT INTO users (id, username, password, email, created_at, role, enabled, storage_quota_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), username, "x", username + "@example.com",
                LocalDate.parse(createdOn).atStartOfDay(), role, true, 1L);
    }
}
