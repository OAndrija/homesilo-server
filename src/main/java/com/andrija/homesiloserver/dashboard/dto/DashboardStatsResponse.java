package com.andrija.homesiloserver.dashboard.dto;

import com.andrija.homesiloserver.file.dto.FileMetadataResponse;

import java.util.List;

/**
 * @param storageUsedBytes everything counted against the quota, trash included
 * @param trashedBytes       the part of {@code storageUsedBytes} that is in trash
 * @param trashRetentionDays days before trashed items are purged automatically; 0 = never
 */
public record DashboardStatsResponse(
        long storageUsedBytes,
        long storageQuotaBytes,
        long trashedBytes,
        long totalFiles,
        long totalFolders,
        long filesThisWeek,
        long starredCount,
        int trashRetentionDays,
        List<StorageBreakdownItem> storageBreakdown,
        List<FileMetadataResponse> recentlyTrashed
) {}
