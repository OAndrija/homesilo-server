package com.andrija.homesiloserver.dto;

import com.andrija.homesiloserver.entity.FileMetadata;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileMetadataResponse(
        UUID id,
        String originalFileName,
        String contentType,
        long size,
        boolean trashed,
        LocalDateTime uploadedAt,
        LocalDateTime lastModified,
        LocalDateTime trashedAt
) {
    public static FileMetadataResponse from(FileMetadata fileMetadata) {
        return new FileMetadataResponse(
                fileMetadata.getId(),
                fileMetadata.getOriginalFileName(),
                fileMetadata.getContentType(),
                fileMetadata.getSize(),
                fileMetadata.isTrashed(),
                fileMetadata.getUploadedAt(),
                fileMetadata.getLastModified(),
                fileMetadata.getTrashedAt()
        );
    }
}
