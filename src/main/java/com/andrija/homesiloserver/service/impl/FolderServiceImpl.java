package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.dto.*;
import com.andrija.homesiloserver.entity.FileMetadata;
import com.andrija.homesiloserver.entity.Folder;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.exception.FolderNotFoundException;
import com.andrija.homesiloserver.exception.UserNotFoundException;
import com.andrija.homesiloserver.repository.FileMetadataRepository;
import com.andrija.homesiloserver.repository.FolderRepository;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.service.FileService;
import com.andrija.homesiloserver.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {
    private final LocalFileStorageService localFileStorageService;
    private final UserRepository userRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository folderRepository;

    @Override
    @Transactional
    public FolderResponse createFolder(CreateFolderRequest createFolderRequest, UUID requesterId) {
        User owner = userRepository.findById(requesterId).orElseThrow(() -> new UserNotFoundException("User not found: " + requesterId));

        Folder parent = null;
        if (createFolderRequest.parentId() != null) {
            parent = getFolderAndVerifyOwner(createFolderRequest.parentId(), requesterId);
            if (parent.isTrashed()) {
                throw new IllegalStateException("Cannot create a folder inside a trashed folder");
            }
        }

        assertNameAvailable(requesterId, createFolderRequest.parentId(), createFolderRequest.name(), null);

        Folder folder = Folder.builder()
                .name(createFolderRequest.name())
                .owner(owner)
                .parent(parent)
                .build();

        Folder saved = folderRepository.save(folder);
        log.info("Created folder '{}' (id='{}') for user '{}'", saved.getName(), saved.getId(), requesterId);
        return FolderResponse.from(saved);
    }

    @Override
    @Transactional
    public FolderResponse renameFolder(UUID folderId, RenameFolderRequest renameFolderRequest, UUID requesterId) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);

        if (folder.isTrashed()) {
            throw new IllegalStateException("Cannot rename a folder inside a trashed folder");
        }

        UUID parentId = folder.getParent() != null ? folder.getParent().getId() : null;
        assertNameAvailable(requesterId, folderId, renameFolderRequest.name(), parentId);

        folder.setName(renameFolderRequest.name());
        return FolderResponse.from(folderRepository.save(folder));
    }

    @Override
    @Transactional
    public FolderResponse moveFolder(UUID folderId, MoveFolderRequest moveFolderRequest, UUID requesterId) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);
        if (folder.isTrashed()) {
            throw new IllegalStateException("Cannot move a trashed folder — restore it first");
        }

        Folder newParent = null;
        if (moveFolderRequest.targetParentId() != null) {
            newParent = getFolderAndVerifyOwner(moveFolderRequest.targetParentId(), requesterId);

            if (newParent.isTrashed()) {
                throw new IllegalStateException("Cannot move a folder into a trashed folder");
            }

            // Prevent circular references: target must not be a descendant of the folder being moved
            if (isDescendantOf(newParent, folder, requesterId)) {
                throw new IllegalStateException("Cannot move a folder into one of its own descendants");
            }
        }

        UUID newParentId = newParent != null ? newParent.getId() : null;
        assertNameAvailable(requesterId, newParentId, folder.getName(), folderId);

        folder.setParent(newParent);
        return FolderResponse.from(folderRepository.save(folder));
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getFolder(UUID folderId, UUID requesterId) {
        return FolderResponse.from(getFolderAndVerifyOwner(folderId, requesterId));
    }

    @Override
    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContents(UUID folderId, UUID requesterId, Pageable pageable) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);

        List<FolderResponse> subfolders = folderRepository
                .findByOwnerIdAndParentIdAndTrashedFalseOrderByNameAsc(requesterId, folderId)
                .stream()
                .map(FolderResponse::from)
                .toList();

        var filePage = fileMetadataRepository
                .findByOwnerIdAndFolderIdAndTrashedFalse(requesterId, folderId, pageable)
                .map(FileMetadataResponse::from);

        List<FolderResponse> breadcrumb = buildBreadcrumb(folder);

        return new FolderContentsResponse(
                FolderResponse.from(folder),
                breadcrumb,
                subfolders,
                PageResponse.from(filePage)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FolderContentsResponse getRootContents(UUID requesterId, Pageable pageable) {
        List<FolderResponse> subfolders = folderRepository
                .findByOwnerIdAndParentIsNullAndTrashedFalseOrderByNameAsc(requesterId)
                .stream()
                .map(FolderResponse::from)
                .toList();

        var filePage = fileMetadataRepository
                .findByOwnerIdAndFolderIsNullAndTrashedFalse(requesterId, pageable)
                .map(FileMetadataResponse::from);

        return new FolderContentsResponse(
                null,           // no "current folder" at root
                List.of(),      // breadcrumb is empty at root
                subfolders,
                PageResponse.from(filePage)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FolderResponse> listTrashedFolders(UUID requesterId, Pageable pageable) {
        return PageResponse.from(
                folderRepository
                        .findTopLevelTrashedFolders(requesterId, pageable)
                        .map(FolderResponse::from)
        );
    }

    @Override
    @Transactional
    public FolderResponse trashFolder(UUID folderId, UUID requesterId) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);
        if (folder.isTrashed()) {
            throw new IllegalStateException("Folder is already in trash");
        }

        // Collect all descendant folder IDs (BFS)
        List<UUID> allFolderIds = collectDescendantIds(folderId, requesterId, false);
        allFolderIds.add(folderId); // include the folder itself

        LocalDateTime now = LocalDateTime.now();

        // Bulk-trash all descendant folders
        List<Folder> foldersToTrash = folderRepository.findAllById(allFolderIds);
        foldersToTrash.forEach(f -> {
            f.setTrashed(true);
            f.setTrashedAt(now);
        });
        folderRepository.saveAll(foldersToTrash);

        // Bulk-trash all files inside these folders
        if (!allFolderIds.isEmpty()) {
            fileMetadataRepository.bulkTrashByOwnerIdAndFolderIds(requesterId, allFolderIds, now);
        }

        log.info("Trashed folder '{}' and {} descendant folder(s) for user '{}'",
                folderId, allFolderIds.size() - 1, requesterId);

        return FolderResponse.from(folder);
    }

    @Override
    @Transactional
    public FolderResponse restoreFolder(UUID folderId, UUID requesterId) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);
        if (!folder.isTrashed()) {
            throw new IllegalStateException("Folder is not in trash");
        }

        // Collect all descendant folder IDs (BFS, searching trashed folders)
        List<UUID> allFolderIds = collectDescendantIds(folderId, requesterId, true);
        allFolderIds.add(folderId);

        // Bulk-restore all folders
        List<Folder> foldersToRestore = folderRepository.findAllById(allFolderIds);
        foldersToRestore.forEach(f -> {
            f.setTrashed(false);
            f.setTrashedAt(null);
        });
        folderRepository.saveAll(foldersToRestore);

        // Bulk-restore all files inside these folders
        if (!allFolderIds.isEmpty()) {
            fileMetadataRepository.bulkRestoreByOwnerIdAndFolderIds(requesterId, allFolderIds);
        }

        log.info("Restored folder '{}' and {} descendant folder(s) for user '{}'",
                folderId, allFolderIds.size() - 1, requesterId);

        return FolderResponse.from(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(UUID folderId, UUID requesterId) {
        Folder folder = getFolderAndVerifyOwner(folderId, requesterId);
        if (!folder.isTrashed()) {
            throw new IllegalStateException("Folder must be in trash before permanently deleting");
        }

        // Collect all descendant folder IDs (trashed)
        List<UUID> allFolderIds = collectDescendantIds(folderId, requesterId, true);
        allFolderIds.add(folderId);

        // Delete all physical files in these folders
        List<FileMetadata> filesToDelete = fileMetadataRepository
                .findAllByOwnerIdAndFolderIds(requesterId, allFolderIds);

        for (FileMetadata file : filesToDelete) {
            try {
                localFileStorageService.delete(requesterId, file.getStoredFileName());
            } catch (Exception e) {
                log.warn("Could not delete physical file '{}' for user '{}' — continuing",
                        file.getStoredFileName(), requesterId, e);
            }
        }

        fileMetadataRepository.deleteAll(filesToDelete);

        // Delete all folders (children first to avoid FK constraint violations,
        // though CASCADE DELETE on the FK would handle this — leaving explicit
        // ordering here for clarity)
        List<Folder> foldersToDelete = folderRepository.findAllById(allFolderIds);
        // Sort: deepest first (no parent pointer in the list, so delete all
        // and let the DB handle FK order via deferred constraints, or delete leaves first)
        // Simplest safe approach: delete files first (done above), then all folders
        // in reverse insertion order (children were created after parents, so their IDs
        // are "newer" — but UUID v4 has no ordering, so we just delete all at once
        // and rely on the DB cascade on parent_id FK)
        folderRepository.deleteAll(foldersToDelete);

        log.info("Permanently deleted folder '{}' and {} descendant folder(s) with {} file(s) for user '{}'",
                folderId, allFolderIds.size() - 1, filesToDelete.size(), requesterId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * BFS traversal collecting all descendant folder IDs of a given folder.
     * Does NOT include the root folder ID itself — callers add it if needed.
     *
     * @param trashed true to search trashed folders, false for active folders
     */
    private List<UUID> collectDescendantIds(UUID rootFolderId, UUID ownerId, boolean trashed) {
        List<UUID> allIds = new ArrayList<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(rootFolderId);

        while (!queue.isEmpty()) {
            List<UUID> currentLevel = new ArrayList<>(queue);
            queue.clear();

            List<Folder> children = trashed
                    ? folderRepository.findByOwnerIdAndParentIdInAndTrashedTrue(ownerId, currentLevel)
                    : folderRepository.findByOwnerIdAndParentIdInAndTrashedFalse(ownerId, currentLevel);

            for (Folder child : children) {
                allIds.add(child.getId());
                queue.add(child.getId());
            }
        }

        return allIds;
    }

    /**
     * Walks up the folder's parent chain to build a breadcrumb list
     * ordered root → current folder.
     * Stops after 50 levels to guard against any accidental circular data.
     */
    private List<FolderResponse> buildBreadcrumb(Folder folder) {
        List<FolderResponse> crumbs = new ArrayList<>();
        Folder current = folder;
        int depth = 0;

        while (current != null && depth < 50) {
            crumbs.add(0, FolderResponse.from(current)); // prepend to maintain root-first order
            current = current.getParent();
            depth++;
        }

        return crumbs;
    }

    /**
     * Checks whether `potentialDescendant` is a descendant of `ancestor`
     * by walking up the parent chain. Used to prevent circular folder moves.
     */
    private boolean isDescendantOf(Folder potentialDescendant, Folder ancestor, UUID ownerId) {
        Folder current = potentialDescendant;
        int depth = 0;

        while (current != null && depth < 50) {
            if (current.getId().equals(ancestor.getId())) return true;
            current = current.getParent();
            depth++;
        }

        return false;
    }

    /**
     * Asserts that no active (non-trashed) folder with the given name exists
     * under the same parent for this owner. Throws if the name is already taken.
     *
     * @param excludeId folder ID to exclude from the check (used during rename)
     */
    private void assertNameAvailable(UUID ownerId, UUID parentId, String name, UUID excludeId) {
        boolean taken;

        if (parentId == null) {
            taken = excludeId == null
                    ? folderRepository.existsByOwnerIdAndParentIsNullAndNameAndTrashedFalse(ownerId, name)
                    : folderRepository.existsByOwnerIdAndParentIsNullAndNameAndTrashedFalseAndIdNot(ownerId, name, excludeId);
        } else {
            taken = excludeId == null
                    ? folderRepository.existsByOwnerIdAndParentIdAndNameAndTrashedFalse(ownerId, parentId, name)
                    : folderRepository.existsByOwnerIdAndParentIdAndNameAndTrashedFalseAndIdNot(ownerId, parentId, name, excludeId);
        }

        if (taken) {
            throw new IllegalArgumentException(
                    "A folder named '" + name + "' already exists in this location"
            );
        }
    }

    private Folder getFolderAndVerifyOwner(UUID folderId, UUID requesterId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + folderId));

        if (!folder.getOwner().getId().equals(requesterId)) {
            throw new FolderNotFoundException("Folder not found: " + folderId); // don't reveal existence
        }

        return folder;
    }
}
