package com.andrija.homesiloserver.backup.dto;

import java.time.Instant;
import java.util.List;

/**
 * Backups as admins see them: what's there, and how the last attempt went.
 *
 * @param enabled   whether nightly backups are on
 * @param location  the folder inside the server container (its own volume)
 * @param lastError why the last attempt failed; null if it worked or none was tried
 * @param running   a backup is being made right now
 */
public record BackupResponse(
        boolean enabled,
        String location,
        String schedule,
        int keep,
        boolean running,
        Instant lastAttemptAt,
        String lastError,
        List<Backup> backups
) {
    public record Backup(String name, long sizeBytes, Instant createdAt) {}
}
