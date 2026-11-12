package com.andrija.homesiloserver.file.dto;

import com.andrija.homesiloserver.file.FileMetadata;
import com.andrija.homesiloserver.folder.Folder;
import com.andrija.homesiloserver.thumbnail.ThumbnailService;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileMetadataResponse(
        UUID id,
        String originalFileName,
        String contentType,
        long size,
        boolean trashed,
        boolean starred,
        LocalDateTime uploadedAt,
        LocalDateTime lastModified,
        LocalDateTime trashedAt,
        UUID folderId,
        String folderName,
        /* HomeSilo can show a small preview of it (GET /files/{id}/thumbnail). */
        boolean thumbnail
) {
    public static FileMetadataResponse from(FileMetadata file) {
        Folder folder = file.getFolder();
        return new FileMetadataResponse(
                file.getId(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSize(),
                file.isTrashed(),
                file.isStarred(),
                file.getUploadedAt(),
                file.getLastModified(),
                file.getTrashedAt(),
                folder != null ? folder.getId() : null,
                folder != null ? folder.getName() : null,
                ThumbnailService.canMake(file.getContentType(), file.getSize())
        );
    }
}
