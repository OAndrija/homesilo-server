package com.andrija.homesiloserver.folder;

import com.andrija.homesiloserver.activity.TargetType;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.file.FileMetadataRepository;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.folder.dto.CreateFolderRequest;
import com.andrija.homesiloserver.folder.dto.FolderContentsResponse;
import com.andrija.homesiloserver.folder.dto.FolderResponse;
import com.andrija.homesiloserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    /** Guards parent-chain walks against corrupt (cyclic) data. */
    private static final int MAX_DEPTH = 256;
    private static final int MAX_SEARCH_RESULTS = 50;

    private final FolderRepository folderRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final ActivityRecorder activity;

    // ── Lookups (also used by other services) ────────────────────────────────

    public Folder getOwned(UUID folderId, UUID ownerId) {
        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new NotFoundException("Folder not found"));
    }

    /** A folder that may receive new content: owned by the user and not in trash. */
    public Folder getWritable(UUID folderId, UUID ownerId) {
        Folder folder = getOwned(folderId, ownerId);
        if (folder.isTrashed()) {
            throw new ConflictException("Folder '" + folder.getName() + "' is in trash");
        }
        return folder;
    }

    /** Breadth-first walk of the subtree below {@code rootId}, excluding the root itself. */
    public List<UUID> collectDescendantIds(UUID rootId, UUID ownerId, boolean trashed) {
        List<UUID> result = new ArrayList<>();
        List<UUID> level = List.of(rootId);
        int depth = 0;
        while (!level.isEmpty() && depth++ < MAX_DEPTH) {
            List<UUID> next = folderRepository.findChildren(ownerId, level, trashed).stream()
                    .map(Folder::getId)
                    .toList();
            result.addAll(next);
            level = next;
        }
        return result;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public FolderResponse getFolder(UUID folderId, UUID ownerId) {
        return FolderResponse.from(getOwned(folderId, ownerId));
    }

    public FolderContentsResponse getRootContents(UUID ownerId, Pageable pageable) {
        List<FolderResponse> subfolders = folderRepository
                .findByOwnerIdAndParentIsNullAndTrashedFalseOrderByNameAsc(ownerId).stream()
                .map(FolderResponse::from)
                .toList();
        var files = fileMetadataRepository
                .findByOwnerIdAndFolderIsNullAndTrashedFalse(ownerId, pageable)
                .map(FileMetadataResponse::from);
        return new FolderContentsResponse(null, List.of(), subfolders, PageResponse.from(files));
    }

    public FolderContentsResponse getContents(UUID folderId, UUID ownerId, Pageable pageable) {
        Folder folder = getOwned(folderId, ownerId);
        List<FolderResponse> subfolders = folderRepository
                .findByOwnerIdAndParentIdAndTrashedFalseOrderByNameAsc(ownerId, folderId).stream()
                .map(FolderResponse::from)
                .toList();
        var files = fileMetadataRepository
                .findByOwnerIdAndFolderIdAndTrashedFalse(ownerId, folderId, pageable)
                .map(FileMetadataResponse::from);
        return new FolderContentsResponse(
                FolderResponse.from(folder), breadcrumb(folder), subfolders, PageResponse.from(files));
    }

    public PageResponse<FolderResponse> listTrashed(UUID ownerId, Pageable pageable) {
        return PageResponse.from(folderRepository.findTopLevelTrashed(ownerId, pageable).map(FolderResponse::from));
    }

    public List<FolderResponse> search(UUID ownerId, String query) {
        return folderRepository
                .findByOwnerIdAndTrashedFalseAndNameContainingIgnoreCaseOrderByNameAsc(ownerId, query.trim()).stream()
                .limit(MAX_SEARCH_RESULTS)
                .map(FolderResponse::from)
                .toList();
    }

    /** Starred folders that aren't in trash, by name; {@code query} narrows them like search. */
    public List<FolderResponse> listStarred(UUID ownerId, String query) {
        List<Folder> folders = query == null || query.isBlank()
                ? folderRepository.findByOwnerIdAndStarredTrueAndTrashedFalseOrderByNameAsc(ownerId)
                : folderRepository.findByOwnerIdAndStarredTrueAndTrashedFalseAndNameContainingIgnoreCaseOrderByNameAsc(
                        ownerId, query.trim());
        return folders.stream().map(FolderResponse::from).toList();
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** Stars or unstars the folder. Its contents keep their own stars. */
    @Transactional
    public FolderResponse toggleStar(UUID folderId, UUID ownerId) {
        Folder folder = getWritable(folderId, ownerId);
        folder.setStarred(!folder.isStarred());
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse create(CreateFolderRequest request, UUID ownerId) {
        String name = request.name().trim();
        Folder parent = request.parentId() != null ? getWritable(request.parentId(), ownerId) : null;
        assertNameAvailable(ownerId, request.parentId(), name, null);

        Folder saved = folderRepository.save(Folder.builder()
                .name(name)
                .owner(userRepository.getReferenceById(ownerId))
                .parent(parent)
                .build());
        log.info("Created folder '{}' ({}) for user {}", saved.getName(), saved.getId(), ownerId);
        activity.record(ActivityAction.FOLDER_CREATED, TargetType.FOLDER, saved.getId(), saved.getName(),
                null, parent != null ? parent.getName() : null);
        return FolderResponse.from(saved);
    }

    @Transactional
    public FolderResponse rename(UUID folderId, String newName, UUID ownerId) {
        Folder folder = getWritable(folderId, ownerId);
        String name = newName.trim();
        assertNameAvailable(ownerId, folder.parentFolderId(), name, folderId);
        String previous = folder.getName();
        folder.setName(name);
        if (!previous.equals(name)) {
            activity.record(ActivityAction.FOLDER_RENAMED, TargetType.FOLDER, folderId, name, previous, name);
        }
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse move(UUID folderId, UUID targetParentId, UUID ownerId) {
        Folder folder = getWritable(folderId, ownerId);

        Folder target = null;
        if (targetParentId != null) {
            target = getWritable(targetParentId, ownerId);
            if (isSelfOrDescendant(target, folder)) {
                throw new ConflictException("A folder cannot be moved into itself or one of its subfolders");
            }
        }

        assertNameAvailable(ownerId, targetParentId, folder.getName(), folderId);
        Folder from = folder.getParent();
        folder.setParent(target);
        activity.record(ActivityAction.FOLDER_MOVED, TargetType.FOLDER, folderId, folder.getName(),
                from != null ? from.getName() : null, target != null ? target.getName() : null);
        return FolderResponse.from(folder);
    }

    /** Moves the folder and its whole subtree (folders and files) to trash. */
    @Transactional
    public FolderResponse trash(UUID folderId, UUID ownerId) {
        Folder folder = getOwned(folderId, ownerId);
        if (folder.isTrashed()) {
            throw new ConflictException("Folder is already in trash");
        }

        List<UUID> ids = new ArrayList<>(collectDescendantIds(folderId, ownerId, false));
        ids.add(folderId);
        LocalDateTime now = LocalDateTime.now();
        folderRepository.markTrashed(ownerId, ids, now);
        fileMetadataRepository.markTrashedInFolders(ownerId, ids, now);

        log.info("Trashed folder {} with {} subfolder(s) for user {}", folderId, ids.size() - 1, ownerId);
        activity.record(ActivityAction.FOLDER_TRASHED, TargetType.FOLDER, folderId, folder.getName());
        return FolderResponse.from(getOwned(folderId, ownerId));
    }

    /**
     * Restores the folder and its subtree. If its original parent is no longer
     * available it lands at the top level; if the name is now taken it gets a
     * numeric suffix rather than failing.
     */
    @Transactional
    public FolderResponse restore(UUID folderId, UUID ownerId) {
        Folder folder = getOwned(folderId, ownerId);
        if (!folder.isTrashed()) {
            throw new ConflictException("Folder is not in trash");
        }

        if (folder.getParent() != null && folder.getParent().isTrashed()) {
            folder.setParent(null);
        }
        folder.setName(uniqueName(ownerId, folder.parentFolderId(), folder.getName(), folderId));

        List<UUID> ids = new ArrayList<>(collectDescendantIds(folderId, ownerId, true));
        ids.add(folderId);
        LocalDateTime now = LocalDateTime.now();
        folderRepository.markRestored(ownerId, ids, now);
        fileMetadataRepository.markRestoredInFolders(ownerId, ids);

        log.info("Restored folder {} with {} subfolder(s) for user {}", folderId, ids.size() - 1, ownerId);
        activity.record(ActivityAction.FOLDER_RESTORED, TargetType.FOLDER, folderId, folder.getName());
        return FolderResponse.from(getOwned(folderId, ownerId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<FolderResponse> breadcrumb(Folder folder) {
        LinkedList<FolderResponse> crumbs = new LinkedList<>();
        Folder current = folder;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            crumbs.addFirst(FolderResponse.from(current));
            current = current.getParent();
        }
        return crumbs;
    }

    /** True if {@code candidate} is {@code ancestor} or lies anywhere beneath it. */
    private static boolean isSelfOrDescendant(Folder candidate, Folder ancestor) {
        Folder current = candidate;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current.getId().equals(ancestor.getId())) return true;
            current = current.getParent();
        }
        return false;
    }

    private void assertNameAvailable(UUID ownerId, UUID parentId, String name, UUID excludeId) {
        if (isNameTaken(ownerId, parentId, name, excludeId)) {
            throw new ConflictException("A folder named '" + name + "' already exists here");
        }
    }

    private String uniqueName(UUID ownerId, UUID parentId, String name, UUID excludeId) {
        if (!isNameTaken(ownerId, parentId, name, excludeId)) return name;
        for (int i = 2; i < 1000; i++) {
            String candidate = name + " (" + i + ")";
            if (!isNameTaken(ownerId, parentId, candidate, excludeId)) return candidate;
        }
        return name + " (" + UUID.randomUUID().toString().substring(0, 8) + ")";
    }

    private boolean isNameTaken(UUID ownerId, UUID parentId, String name, UUID excludeId) {
        if (parentId == null) {
            return excludeId == null
                    ? folderRepository.existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndTrashedFalse(ownerId, name)
                    : folderRepository.existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndTrashedFalseAndIdNot(ownerId, name, excludeId);
        }
        return excludeId == null
                ? folderRepository.existsByOwnerIdAndParentIdAndNameIgnoreCaseAndTrashedFalse(ownerId, parentId, name)
                : folderRepository.existsByOwnerIdAndParentIdAndNameIgnoreCaseAndTrashedFalseAndIdNot(ownerId, parentId, name, excludeId);
    }
}
