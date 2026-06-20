package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.dto.PageResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileService {
    FileMetadataResponse upload(MultipartFile file, UUID requesterId);
    Resource download(UUID fileId, UUID requesterId);
    PageResponse<FileMetadataResponse> listFiles(UUID requesterId, Pageable pageable);
    PageResponse<FileMetadataResponse> listTrashedFiles(UUID requesterId, Pageable pageable);
    PageResponse<FileMetadataResponse> searchFiles(UUID requesterId, String query, Pageable pageable);
    FileMetadataResponse getFileMetadata(UUID fileId, UUID requesterId);
    FileMetadataResponse trashFile(UUID fileId, UUID requesterId);
    FileMetadataResponse restore(UUID fileId, UUID requesterId);
    void deletePermanently(UUID fileId, UUID requesterId);
    long getStorageUsed(UUID requesterId);
}
