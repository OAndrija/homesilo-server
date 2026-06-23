package com.andrija.homesiloserver.dto;

import com.andrija.homesiloserver.entity.Folder;

import java.time.LocalDateTime;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentId,       // null = root-level folder
        String parentName,   // null = root-level folder
        boolean trashed,
        LocalDateTime trashedAt,
        LocalDateTime createdAt,
        LocalDateTime lastModified
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParent() != null ? folder.getParent().getId()   : null,
                folder.getParent() != null ? folder.getParent().getName() : null,
                folder.isTrashed(),
                folder.getTrashedAt(),
                folder.getCreatedAt(),
                folder.getLastModified()
        );
    }
}
