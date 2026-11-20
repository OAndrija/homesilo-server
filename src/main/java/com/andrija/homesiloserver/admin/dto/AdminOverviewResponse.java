package com.andrija.homesiloserver.admin.dto;

import java.util.List;
import java.util.UUID;

/**
 * Server-wide figures for the top of the admin page.
 *
 * @param storageAllocatedBytes the sum of every account's quota
 * @param defaultQuotaBytes     the quota new accounts get unless the admin picks another
 * @param topUsers              the accounts using the most storage, largest first (at most five)
 */
public record AdminOverviewResponse(
        long users,
        long activeUsers,
        long admins,
        long storageUsedBytes,
        long storageAllocatedBytes,
        long files,
        long defaultQuotaBytes,
        boolean registrationEnabled,
        List<StorageByUser> topUsers,
        /* The running release, e.g. "1.2.0"; "dev" when run from source. */
        String version
) {
    public record StorageByUser(UUID id, String username, long bytes) {}
}
