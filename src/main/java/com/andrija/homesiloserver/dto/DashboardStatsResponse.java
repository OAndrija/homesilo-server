package com.andrija.homesiloserver.dto;

import java.util.List;

public record DashboardStatsResponse(
        long storageUsedBytes,
        long storageQuotaBytes,
        long totalFiles,
        long filesThisWeek,
        List<StorageBreakdownItem> storageBreakdown,
        List<FileMetadataResponse> recentlyTrashed
) {
}
