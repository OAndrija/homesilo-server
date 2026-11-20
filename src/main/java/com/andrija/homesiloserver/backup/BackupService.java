package com.andrija.homesiloserver.backup;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.backup.dto.BackupResponse;
import com.andrija.homesiloserver.common.exception.ApiException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dumps the database with {@code pg_dump} every night into the backup folder,
 * keeping the newest few. A dump is everything HomeSilo knows apart from the
 * file contents themselves (accounts, folders, names, sharing, settings), so
 * together with a copy of the files volume it's a complete backup. See
 * docs/backups.md for taking both off the machine and restoring.
 *
 * <p>Dumps can't be downloaded through the app on purpose: they hold every
 * account's password hash, so they stay on the server's disk.
 */
@Slf4j
@Service
public class BackupService {

    static final String PREFIX = "homesilo-";
    static final String SUFFIX = ".dump";
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC);
    private static final long TIMEOUT_MINUTES = 30;

    private final BackupProperties properties;
    private final String databaseUrl;
    private final String databaseUser;
    private final String databasePassword;
    private final ActivityRecorder activity;
    private final Path dir;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Instant lastAttemptAt;
    private volatile String lastError;

    public BackupService(BackupProperties properties, ActivityRecorder activity,
                         @Value("${spring.datasource.url}") String databaseUrl,
                         @Value("${spring.datasource.username:}") String databaseUser,
                         @Value("${spring.datasource.password:}") String databasePassword) {
        this.properties = properties;
        this.databaseUrl = databaseUrl;
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;
        this.activity = activity;
        this.dir = Path.of(properties.path()).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "${homesilo.backup.cron:0 0 2 * * *}")
    public void nightly() {
        if (!properties.enabled()) return;
        try {
            backUp();
        } catch (RuntimeException ex) {
            // Already recorded as the last error, and logged.
        }
    }

    /** Makes a backup now. Takes seconds for a household's database. */
    public BackupResponse.Backup backUp() {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("A backup is already being made");
        }
        lastAttemptAt = Instant.now();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(PREFIX + NAME.format(lastAttemptAt) + SUFFIX);
            Path partial = dir.resolve(target.getFileName() + ".part");
            dump(partial);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            lastError = null;
            pruneOld();
            BackupResponse.Backup made = describe(target);
            log.info("Database backed up to {} ({} bytes)", target, made.sizeBytes());
            return made;
        } catch (IOException | BackupFailedException ex) {
            lastError = ex instanceof BackupFailedException ? ex.getMessage()
                    : "Couldn't write to " + dir + ": " + ex.getMessage();
            log.error("Database backup failed: {}", lastError);
            activity.recordSystem(ActivityAction.BACKUP_FAILED, lastError);
            throw new BackupFailedException(lastError);
        } finally {
            running.set(false);
        }
    }

    public BackupResponse status() {
        return new BackupResponse(properties.enabled(), dir.toString(), properties.cron(), properties.keep(),
                running.get(), lastAttemptAt, lastError, list());
    }

    List<BackupResponse.Backup> list() {
        if (!Files.isDirectory(dir)) return List.of();
        List<BackupResponse.Backup> found = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, PREFIX + "*" + SUFFIX)) {
            for (Path file : files) found.add(describe(file));
        } catch (IOException ex) {
            log.warn("Couldn't list backups in {}", dir, ex);
        }
        found.sort(Comparator.comparing(BackupResponse.Backup::createdAt).reversed());
        return found;
    }

    private void dump(Path out) throws IOException {
        Connection db = Connection.parse(databaseUrl);
        ProcessBuilder command = new ProcessBuilder(
                properties.command(), "--format=custom", "--no-owner", "--no-privileges",
                "--host", db.host(), "--port", String.valueOf(db.port()),
                "--username", databaseUser, "--dbname", db.database(),
                "--file", out.toString())
                .redirectErrorStream(true);
        // The password goes in the environment, never on the command line where `ps` would show it.
        command.environment().put("PGPASSWORD", databasePassword);
        Process process;
        try {
            process = command.start();
        } catch (IOException ex) {
            throw new BackupFailedException("pg_dump isn't installed here. The HomeSilo image has it; "
                    + "when running from source, install the PostgreSQL client tools.");
        }
        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new BackupFailedException("pg_dump took longer than " + TIMEOUT_MINUTES + " minutes");
            }
            if (process.exitValue() != 0) {
                Files.deleteIfExists(out);
                throw new BackupFailedException("pg_dump failed: " + lastLine(output));
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BackupFailedException("The backup was interrupted");
        }
    }

    private void pruneOld() throws IOException {
        List<BackupResponse.Backup> all = list();
        for (int i = properties.keep(); i < all.size(); i++) {
            Files.deleteIfExists(dir.resolve(all.get(i).name()));
        }
    }

    private static BackupResponse.Backup describe(Path file) throws IOException {
        return new BackupResponse.Backup(file.getFileName().toString(), Files.size(file),
                Files.getLastModifiedTime(file).toInstant());
    }

    private static String lastLine(String output) {
        if (output.isEmpty()) return "no output";
        String[] lines = output.split("\n");
        return lines[lines.length - 1];
    }

    /** Host, port and database name from a jdbc:postgresql:// URL. */
    record Connection(String host, int port, String database) {
        static Connection parse(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new BackupFailedException("Backups need a PostgreSQL database URL, not " + jdbcUrl);
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            return new Connection(uri.getHost(), uri.getPort() == -1 ? 5432 : uri.getPort(),
                    path.isEmpty() ? "postgres" : path);
        }
    }

    public static class BackupFailedException extends ApiException {
        BackupFailedException(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }
}
