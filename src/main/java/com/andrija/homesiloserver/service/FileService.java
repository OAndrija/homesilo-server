package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.dto.PageResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FileService {
    FileMetadataResponse upload(MultipartFile file, UUID requesterId, UUID folderId);

    Resource download(UUID fileId, UUID requesterId);
    Resource downloadAsZip(List<UUID> fileIds, UUID requesterId);

    PageResponse<FileMetadataResponse> listFiles(UUID requesterId, Pageable pageable);
    PageResponse<FileMetadataResponse> listTrashedFiles(UUID requesterId, Pageable pageable);
    PageResponse<FileMetadataResponse> listStarredFiles(UUID requesterId, Pageable pageable);

    PageResponse<FileMetadataResponse> searchFiles(UUID requesterId, String query, Pageable pageable);
    PageResponse<FileMetadataResponse> searchTrashedFiles(UUID requesterId, String query, Pageable pageable);
    PageResponse<FileMetadataResponse> searchStarredFiles(UUID requesterId, String query, Pageable pageable);

    FileMetadataResponse getFileMetadata(UUID fileId, UUID requesterId);
    FileMetadataResponse trashFile(UUID fileId, UUID requesterId);
    FileMetadataResponse restore(UUID fileId, UUID requesterId);
    FileMetadataResponse toggleStar(UUID fileId, UUID requesterId);
    DashboardStatsResponse getDashboardStats(UUID requesterId);

    FileMetadataResponse moveToFolder(UUID fileId, UUID targetFolderId, UUID requesterId);

    void deletePermanently(UUID fileId, UUID requesterId);
    long getStorageUsed(UUID requesterId);
    long getStarredCount(UUID requesterId);

}
