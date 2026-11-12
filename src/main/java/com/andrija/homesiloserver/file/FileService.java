package com.andrija.homesiloserver.file;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.activity.TargetType;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.common.exception.QuotaExceededException;
import com.andrija.homesiloserver.common.exception.StorageException;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.folder.Folder;
import com.andrija.homesiloserver.folder.FolderService;
import com.andrija.homesiloserver.storage.BlobStorage.StoredBlob;
import com.andrija.homesiloserver.storage.BlobStorage;
import com.andrija.homesiloserver.storage.BlobsReleasedEvent;
import com.andrija.homesiloserver.thumbnail.ThumbnailWanted;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final FileMetadataRepository fileRepository;
    private final FolderService folderService;
    private final UserService userService;
    private final BlobStorage blobStorage;
    private final ApplicationEventPublisher events;
    private final ActivityRecorder activity;
    private final Tika tika = new Tika();

    // ── Upload ───────────────────────────────────────────────────────────────

    /**
     * Stores the upload and creates a file entry for it. Uploading the same bytes
     * with the same name into the same folder again is idempotent and returns the
     * existing entry (safe for client retries).
     */
    @Transactional
    public FileMetadataResponse upload(MultipartFile file, UUID ownerId, UUID folderId) {
        User owner = userService.getEntity(ownerId);
        Folder folder = folderId != null ? folderService.getWritable(folderId, ownerId) : null;
        String name = sanitizeFileName(file.getOriginalFilename());

        long used = fileRepository.sumSizeByOwnerId(ownerId);
        if (used + file.getSize() > owner.getStorageQuotaBytes()) {
            throw new QuotaExceededException("Not enough storage space left for '" + name + "'");
        }

        StoredBlob blob;
        try (InputStream in = file.getInputStream()) {
            blob = blobStorage.store(ownerId, in);
        } catch (IOException ex) {
            throw new StorageException("Could not read upload", ex);
        }

        Optional<FileMetadata> duplicate = folder == null
                ? fileRepository.findFirstByOwnerIdAndFolderIsNullAndOriginalFileNameAndStoredFileNameAndTrashedFalse(
                        ownerId, name, blob.hash())
                : fileRepository.findFirstByOwnerIdAndFolderIdAndOriginalFileNameAndStoredFileNameAndTrashedFalse(
                        ownerId, folder.getId(), name, blob.hash());
        if (duplicate.isPresent()) {
            log.debug("Duplicate upload of '{}' by user {}; returning existing entry", name, ownerId);
            return FileMetadataResponse.from(duplicate.get());
        }

        FileMetadata saved = fileRepository.save(FileMetadata.builder()
                .originalFileName(name)
                .storedFileName(blob.hash())
                .contentType(detectContentType(ownerId, blob.hash(), name))
                .size(blob.size())
                .owner(owner)
                .folder(folder)
                .build());

        log.info("Uploaded '{}' ({} bytes) as {} for user {}", name, blob.size(), saved.getId(), ownerId);
        activity.record(ActivityAction.FILE_UPLOADED, TargetType.FILE, saved.getId(), name,
                null, String.valueOf(blob.size()));
        events.publishEvent(new ThumbnailWanted(ownerId, blob.hash(), saved.getContentType(), blob.size()));
        return FileMetadataResponse.from(saved);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public FileMetadataResponse getMetadata(UUID fileId, UUID ownerId) {
        return FileMetadataResponse.from(getOwned(fileId, ownerId));
    }

    public PageResponse<FileMetadataResponse> listActive(UUID ownerId, Pageable pageable) {
        return page(fileRepository.findByOwnerIdAndTrashedFalse(ownerId, pageable));
    }

    public PageResponse<FileMetadataResponse> listStarred(UUID ownerId, Pageable pageable) {
        return page(fileRepository.findByOwnerIdAndStarredTrueAndTrashedFalse(ownerId, pageable));
    }

    public PageResponse<FileMetadataResponse> listTrashed(UUID ownerId, Pageable pageable) {
        return page(fileRepository.findTopLevelTrashed(ownerId, pageable));
    }

    public PageResponse<FileMetadataResponse> searchActive(UUID ownerId, String query, Pageable pageable) {
        return page(fileRepository.findByOwnerIdAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
                ownerId, query.trim(), pageable));
    }

    public PageResponse<FileMetadataResponse> searchStarred(UUID ownerId, String query, Pageable pageable) {
        return page(fileRepository.findByOwnerIdAndStarredTrueAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
                ownerId, query.trim(), pageable));
    }

    public PageResponse<FileMetadataResponse> searchTrashed(UUID ownerId, String query, Pageable pageable) {
        return page(fileRepository.findTopLevelTrashedByName(ownerId, likePattern(query), pageable));
    }

    /** What a thumbnail is made from. Trashed files have one too, for the trash view. */
    public ThumbnailSource getThumbnailSource(UUID fileId, UUID ownerId) {
        FileMetadata file = getOwned(fileId, ownerId);
        return new ThumbnailSource(ownerId, file.getStoredFileName(), file.getContentType(), file.getSize());
    }

    public record ThumbnailSource(UUID ownerId, String hash, String contentType, long size) {}

    /** Resolves an active file and its bytes for download or preview. */
    public FileContent getContent(UUID fileId, UUID ownerId) {
        FileMetadata file = getOwned(fileId, ownerId);
        if (file.isTrashed()) {
            throw new ConflictException("File is in trash — restore it first");
        }
        Resource resource = blobStorage.load(ownerId, file.getStoredFileName());
        return new FileContent(file.getOriginalFileName(), file.getContentType(), file.getSize(), resource);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public FileMetadataResponse rename(UUID fileId, String newName, UUID ownerId) {
        FileMetadata file = getActive(fileId, ownerId);
        String name = newName.trim();
        requireSameExtension(file.getOriginalFileName(), name);
        String previous = file.getOriginalFileName();
        file.setOriginalFileName(name);
        if (!previous.equals(name)) {
            activity.record(ActivityAction.FILE_RENAMED, TargetType.FILE, fileId, name, previous, name);
        }
        return FileMetadataResponse.from(file);
    }

    /**
     * A rename can't change the extension: the file's type was detected when it was
     * uploaded, so "report.pdf" renamed to "report.i" would still be a PDF in previews
     * but download as an unknown ".i" file. Case may change (.PDF → .pdf), and files
     * that had no extension can be named freely.
     */
    static void requireSameExtension(String currentName, String newName) {
        String current = extensionOf(currentName);
        if (current.isEmpty()) return;
        if (!extensionOf(newName).equalsIgnoreCase(current)) {
            throw new BadRequestException(
                    "Keep the ." + current + " extension. Renaming doesn't change the file's type.");
        }
    }

    /** The text after the last dot, or "" (also for dot-files like ".env"). */
    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    @Transactional
    public FileMetadataResponse move(UUID fileId, UUID targetFolderId, UUID ownerId) {
        FileMetadata file = getActive(fileId, ownerId);
        Folder from = file.getFolder();
        Folder to = targetFolderId != null ? folderService.getWritable(targetFolderId, ownerId) : null;
        file.setFolder(to);
        activity.record(ActivityAction.FILE_MOVED, TargetType.FILE, fileId, file.getOriginalFileName(),
                from != null ? from.getName() : null, to != null ? to.getName() : null);
        return FileMetadataResponse.from(file);
    }

    @Transactional
    public FileMetadataResponse toggleStar(UUID fileId, UUID ownerId) {
        FileMetadata file = getActive(fileId, ownerId);
        file.setStarred(!file.isStarred());
        return FileMetadataResponse.from(file);
    }

    @Transactional
    public FileMetadataResponse trash(UUID fileId, UUID ownerId) {
        FileMetadata file = getActive(fileId, ownerId);
        file.setTrashed(true);
        file.setTrashedAt(LocalDateTime.now());
        activity.record(ActivityAction.FILE_TRASHED, TargetType.FILE, fileId, file.getOriginalFileName());
        return FileMetadataResponse.from(file);
    }

    /** Restores the file; if its folder is still in trash, the file lands at the top level instead. */
    @Transactional
    public FileMetadataResponse restore(UUID fileId, UUID ownerId) {
        FileMetadata file = getOwned(fileId, ownerId);
        if (!file.isTrashed()) {
            throw new ConflictException("File is not in trash");
        }
        if (file.getFolder() != null && file.getFolder().isTrashed()) {
            file.setFolder(null);
        }
        file.setTrashed(false);
        file.setTrashedAt(null);
        activity.record(ActivityAction.FILE_RESTORED, TargetType.FILE, fileId, file.getOriginalFileName());
        return FileMetadataResponse.from(file);
    }

    @Transactional
    public void deletePermanently(UUID fileId, UUID ownerId) {
        FileMetadata file = getOwned(fileId, ownerId);
        if (!file.isTrashed()) {
            throw new ConflictException("Move the file to trash before deleting it permanently");
        }
        fileRepository.delete(file);
        activity.record(ActivityAction.FILE_DELETED, TargetType.FILE, fileId, file.getOriginalFileName());
        events.publishEvent(new BlobsReleasedEvent(ownerId, List.of(file.getStoredFileName())));
        log.info("Permanently deleted file {} for user {}", fileId, ownerId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FileMetadata getOwned(UUID fileId, UUID ownerId) {
        return fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));
    }

    private FileMetadata getActive(UUID fileId, UUID ownerId) {
        FileMetadata file = getOwned(fileId, ownerId);
        if (file.isTrashed()) {
            throw new ConflictException("File is in trash — restore it first");
        }
        return file;
    }

    private static PageResponse<FileMetadataResponse> page(org.springframework.data.domain.Page<FileMetadata> page) {
        return PageResponse.from(page.map(FileMetadataResponse::from));
    }

    /** Detects the type from the stored bytes (magic numbers), using the name only as a hint. */
    private String detectContentType(UUID ownerId, String hash, String fileName) {
        try (InputStream in = blobStorage.load(ownerId, hash).getInputStream()) {
            String detected = tika.detect(in, fileName);
            return detected != null ? detected : FALLBACK_CONTENT_TYPE;
        } catch (IOException ex) {
            log.warn("Content-type detection failed for '{}'", fileName, ex);
            return FALLBACK_CONTENT_TYPE;
        }
    }

    /** Strips any client-supplied path and characters that are invalid in a file name. */
    static String sanitizeFileName(String original) {
        if (original == null) return "unnamed";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return "unnamed";
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 16 ? name.substring(dot) : "";
            name = name.substring(0, 255 - ext.length()) + ext;
        }
        return name;
    }

    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Search query must not be blank");
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public record FileContent(String fileName, String contentType, long size, Resource resource) {}
}
