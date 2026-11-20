package com.andrija.homesiloserver.backup;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.backup.dto.BackupResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Runs a stand-in for pg_dump; the real one is exercised by the fresh-install E2E job. */
class BackupServiceTest {

    @TempDir
    Path tmp;

    private final ActivityRecorder activity = mock(ActivityRecorder.class);

    @Test
    void aBackupIsWrittenWithTheRightArgumentsAndNoPasswordOnTheCommandLine() throws Exception {
        Path args = tmp.resolve("args.txt");
        Path fake = script("""
                echo "$@" > %s
                echo "PGPASSWORD=$PGPASSWORD" >> %s
                while [ "$1" != "--file" ]; do shift; done
                echo "dump" > "$2"
                """.formatted(args, args));

        BackupResponse.Backup made = service(fake, 7).backUp();

        assertThat(made.name()).matches("homesilo-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.dump");
        assertThat(Files.readString(tmp.resolve("backups").resolve(made.name()))).isEqualTo("dump\n");
        String called = Files.readString(args);
        assertThat(called).contains("--format=custom", "--host db", "--port 5433", "--username homesilo",
                "--dbname silo");
        assertThat(called.lines().findFirst().orElseThrow()).doesNotContain("secret-password");
        assertThat(called).contains("PGPASSWORD=secret-password");
    }

    @Test
    void onlyTheNewestAreKept() throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        for (int i = 1; i <= 3; i++) {
            Path old = Files.writeString(backups.resolve("homesilo-2026-01-0" + i + "-020000.dump"), "old");
            Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-01-0" + i + "T02:00:00Z")));
        }
        Path fake = script("while [ \"$1\" != \"--file\" ]; do shift; done; echo new > \"$2\"");

        service(fake, 2).backUp();

        assertThat(service(fake, 2).status().backups()).hasSize(2)
                .first().satisfies(b -> assertThat(b.createdAt()).isAfter(Instant.parse("2026-02-01T00:00:00Z")));
        assertThat(backups.resolve("homesilo-2026-01-01-020000.dump")).doesNotExist();
    }

    @Test
    void aFailureIsReportedAndLeavesNoHalfFile() throws Exception {
        Path fake = script("""
                while [ "$1" != "--file" ]; do shift; done
                echo partial > "$2"
                echo 'pg_dump: error: connection to server failed: password authentication failed' >&2
                exit 1
                """);
        BackupService service = service(fake, 7);

        assertThatThrownBy(service::backUp).hasMessageContaining("password authentication failed");

        BackupResponse status = service.status();
        assertThat(status.lastError()).contains("password authentication failed");
        assertThat(status.lastAttemptAt()).isNotNull();
        assertThat(status.backups()).isEmpty();
        try (var left = Files.list(tmp.resolve("backups"))) {
            assertThat(left).isEmpty();
        }
        verify(activity).recordSystem(eq(ActivityAction.BACKUP_FAILED), contains("password authentication failed"));
    }

    @Test
    void aMissingPgDumpIsExplained() {
        BackupService service = service(tmp.resolve("no-such-pg_dump"), 7);
        assertThatThrownBy(service::backUp).hasMessageContaining("pg_dump isn't installed");
    }

    @Test
    void readsTheConnectionFromTheJdbcUrl() {
        assertThat(BackupService.Connection.parse("jdbc:postgresql://db:5432/homesilo?sslmode=disable"))
                .isEqualTo(new BackupService.Connection("db", 5432, "homesilo"));
        assertThat(BackupService.Connection.parse("jdbc:postgresql://localhost/files"))
                .isEqualTo(new BackupService.Connection("localhost", 5432, "files"));
        assertThatThrownBy(() -> BackupService.Connection.parse("jdbc:h2:mem:test"))
                .hasMessageContaining("PostgreSQL");
    }

    private BackupService service(Path command, int keep) {
        return new BackupService(
                new BackupProperties(true, tmp.resolve("backups").toString(), keep, "0 0 2 * * *", command.toString()),
                activity, new BackupService.Database("jdbc:postgresql://db:5433/silo", "homesilo", "secret-password"));
    }

    private Path script(String body) throws Exception {
        Path script = tmp.resolve("pg_dump-" + System.nanoTime());
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }
}
