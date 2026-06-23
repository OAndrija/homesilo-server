package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FolderService {
    FolderResponse createFolder(CreateFolderRequest createFolderRequest, UUID requesterId);
    FolderResponse renameFolder(UUID folderId, RenameFolderRequest renameFolderRequest, UUID requesterId);
    FolderResponse moveFolder(UUID folderId, MoveFolderRequest moveFolderRequest, UUID requesterId);
    FolderResponse getFolder(UUID folderId, UUID requesterId);

    FolderContentsResponse getFolderContents(UUID folderId, UUID requesterId, Pageable pageable);
    FolderContentsResponse getRootContents(UUID requesterId, Pageable pageable);

    FolderResponse trashFolder(UUID folderId, UUID requesterId);
    FolderResponse restoreFolder(UUID folderId, UUID requesterId);

    void deleteFolder(UUID folderId, UUID requesterId);
}
