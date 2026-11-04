package com.andrija.homesiloserver.folder.dto;

import com.andrija.homesiloserver.folder.Folder;

import java.time.LocalDateTime;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentId,
        String parentName,
        boolean trashed,
        LocalDateTime trashedAt,
        LocalDateTime createdAt,
        LocalDateTime lastModified
) {
    public static FolderResponse from(Folder folder) {
        Folder parent = folder.getParent();
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                parent != null ? parent.getId() : null,
                parent != null ? parent.getName() : null,
                folder.isTrashed(),
                folder.getTrashedAt(),
                folder.getCreatedAt(),
                folder.getLastModified()
        );
    }
}
