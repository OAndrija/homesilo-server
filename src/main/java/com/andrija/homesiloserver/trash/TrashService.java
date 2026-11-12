package com.andrija.homesiloserver.trash;

import com.andrija.homesiloserver.activity.TargetType;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.file.FileMetadata;
import com.andrija.homesiloserver.file.FileMetadataRepository;
import com.andrija.homesiloserver.folder.Folder;
import com.andrija.homesiloserver.folder.FolderRepository;
import com.andrija.homesiloserver.folder.FolderService;
import com.andrija.homesiloserver.storage.BlobsReleasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Permanent deletion. Uses bulk statements so a folder tree of any size is
 * removed in a constant number of queries; blobs are released after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

    private final FolderService folderService;
    private final FolderRepository folderRepository;
    private final FileMetadataRepository fileRepository;
    private final ApplicationEventPublisher events;
    private final HomesiloProperties properties;
    private final ActivityRecorder activity;

    /** Deletes a trashed folder and everything under it. */
    @Transactional
    public PurgeResult deleteFolder(UUID folderId, UUID ownerId) {
        Folder folder = folderService.getOwned(folderId, ownerId);
        if (!folder.isTrashed()) {
            throw new ConflictException("Move the folder to trash before deleting it permanently");
        }
        List<UUID> ids = new ArrayList<>(folderService.collectDescendantIds(folderId, ownerId, true));
        ids.add(folderId);
        String name = folder.getName();
        PurgeResult result = purgeFolders(ownerId, ids, new HashSet<>());
        log.info("Permanently deleted folder {} ({} folder(s), {} file(s)) for user {}",
                folderId, result.folders(), result.files(), ownerId);
        activity.record(ActivityAction.FOLDER_DELETED, TargetType.FOLDER, folderId, name,
                null, String.valueOf(result.files()));
        return result;
    }

    /** Deletes everything in the user's trash. */
    @Transactional
    public PurgeResult emptyTrash(UUID ownerId) {
        Set<String> blobs = new HashSet<>(fileRepository.findTrashedBlobs(ownerId));
        int files = fileRepository.deleteTrashed(ownerId);

        List<UUID> folderIds = folderRepository.findTrashedIds(ownerId);
        PurgeResult folders = purgeFolders(ownerId, folderIds, blobs);

        PurgeResult result = new PurgeResult(files + folders.files(), folders.folders());
        log.info("Emptied trash for user {}: {} file(s), {} folder(s)", ownerId, result.files(), result.folders());
        if (result.files() + result.folders() > 0) {
            activity.record(ActivityAction.TRASH_EMPTIED, null, null, null,
                    null, String.valueOf(result.files() + result.folders()));
        }
        return result;
    }

    /** Purges items that have been in trash longer than the configured retention. */
    @Transactional
    public PurgeResult purgeExpired() {
        int retentionDays = properties.trash().retentionDays();
        if (retentionDays <= 0) return new PurgeResult(0, 0);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        int files = 0;
        int folders = 0;
        for (Folder folder : folderRepository.findTopLevelTrashedBefore(cutoff)) {
            UUID ownerId = folder.getOwner().getId();
            List<UUID> ids = new ArrayList<>(folderService.collectDescendantIds(folder.getId(), ownerId, true));
            ids.add(folder.getId());
            PurgeResult result = purgeFolders(ownerId, ids, new HashSet<>());
            files += result.files();
            folders += result.folders();
        }

        Map<UUID, List<FileMetadata>> expiredFiles = fileRepository.findByTrashedTrueAndTrashedAtBefore(cutoff).stream()
                .collect(Collectors.groupingBy(f -> f.getOwner().getId()));
        for (var entry : expiredFiles.entrySet()) {
            fileRepository.deleteAllInBatch(entry.getValue());
            files += entry.getValue().size();
            releaseBlobs(entry.getKey(), entry.getValue().stream().map(FileMetadata::getStoredFileName).toList());
        }

        if (files > 0 || folders > 0) {
            log.info("Trash retention ({} days): purged {} file(s) and {} folder(s)", retentionDays, files, folders);
            activity.recordSystem(ActivityAction.TRASH_EXPIRED, String.valueOf(files + folders));
        }
        return new PurgeResult(files, folders);
    }

    private PurgeResult purgeFolders(UUID ownerId, Collection<UUID> folderIds, Set<String> blobs) {
        if (folderIds.isEmpty()) {
            releaseBlobs(ownerId, blobs);
            return new PurgeResult(0, 0);
        }
        blobs.addAll(fileRepository.findBlobsInFolders(ownerId, folderIds));
        int files = fileRepository.deleteInFolders(ownerId, folderIds);
        folderRepository.detachFromParents(ownerId, folderIds);
        int folders = folderRepository.deleteByIds(ownerId, folderIds);
        releaseBlobs(ownerId, blobs);
        return new PurgeResult(files, folders);
    }

    private void releaseBlobs(UUID ownerId, Collection<String> blobs) {
        if (!blobs.isEmpty()) {
            events.publishEvent(new BlobsReleasedEvent(ownerId, List.copyOf(blobs)));
        }
    }

    public record PurgeResult(int files, int folders) {}
}
