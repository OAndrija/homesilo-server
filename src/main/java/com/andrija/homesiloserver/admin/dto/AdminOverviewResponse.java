package com.andrija.homesiloserver.admin.dto;

/**
 * Server-wide figures for the top of the admin page.
 *
 * @param storageAllocatedBytes the sum of every account's quota
 * @param defaultQuotaBytes     the quota new accounts get unless the admin picks another
 */
public record AdminOverviewResponse(
        long users,
        long activeUsers,
        long admins,
        long storageUsedBytes,
        long storageAllocatedBytes,
        long files,
        long defaultQuotaBytes,
        boolean registrationEnabled
) {}
