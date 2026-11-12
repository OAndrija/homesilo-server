package com.andrija.homesiloserver.dashboard;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.dashboard.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.dashboard.dto.StorageBreakdownItem;
import com.andrija.homesiloserver.file.FileCategory;
import com.andrija.homesiloserver.file.FileMetadataRepository;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.folder.FolderRepository;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final String TRASH_CATEGORY = "Trash";

    private final FileMetadataRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserService userService;
    private final HomesiloProperties properties;

    public DashboardStatsResponse getStats(UUID ownerId) {
        User user = userService.getEntity(ownerId);
        long trashed = fileRepository.sumTrashedSizeByOwnerId(ownerId);

        List<FileMetadataResponse> recentlyTrashed = fileRepository
                .findTopLevelTrashed(ownerId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "trashedAt")))
                .map(FileMetadataResponse::from)
                .getContent();

        return new DashboardStatsResponse(
                fileRepository.sumSizeByOwnerId(ownerId),
                user.getStorageQuotaBytes(),
                trashed,
                fileRepository.countByOwnerIdAndTrashedFalse(ownerId),
                folderRepository.countByOwnerIdAndTrashedFalse(ownerId),
                fileRepository.countByOwnerIdAndTrashedFalseAndUploadedAtAfter(ownerId, LocalDateTime.now().minusDays(7)),
                fileRepository.countByOwnerIdAndStarredTrueAndTrashedFalse(ownerId)
                        + folderRepository.countByOwnerIdAndStarredTrueAndTrashedFalse(ownerId),
                properties.trash().retentionDays(),
                breakdown(ownerId, trashed),
                recentlyTrashed
        );
    }

    private List<StorageBreakdownItem> breakdown(UUID ownerId, long trashedBytes) {
        Map<FileCategory, Long> totals = new EnumMap<>(FileCategory.class);
        for (var row : fileRepository.sumActiveSizeByContentType(ownerId)) {
            long bytes = row.getBytes() != null ? row.getBytes() : 0L;
            totals.merge(FileCategory.of(row.getContentType()), bytes, Long::sum);
        }

        List<StorageBreakdownItem> items = new ArrayList<>();
        totals.forEach((category, bytes) -> {
            if (bytes > 0) items.add(new StorageBreakdownItem(category.label(), bytes));
        });
        if (trashedBytes > 0) {
            items.add(new StorageBreakdownItem(TRASH_CATEGORY, trashedBytes));
        }
        return items;
    }
}
