package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.dto.PageResponse;
import com.andrija.homesiloserver.dto.StorageBreakdownItem;
import com.andrija.homesiloserver.entity.FileMetadata;
import com.andrija.homesiloserver.entity.Folder;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.exception.FileNotFoundException;
import com.andrija.homesiloserver.exception.FileStorageException;
import com.andrija.homesiloserver.exception.FolderNotFoundException;
import com.andrija.homesiloserver.exception.UserNotFoundException;
import com.andrija.homesiloserver.repository.FileMetadataRepository;
import com.andrija.homesiloserver.repository.FolderRepository;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.service.FileService;
import com.andrija.homesiloserver.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageService fileStorageService;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final Tika tika;

    @Override
    @Transactional
    public FileMetadataResponse upload(MultipartFile file, UUID requesterId, UUID folderId) {
        User owner = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + requesterId));

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .filter(f -> f.getOwner().getId().equals(requesterId))
                    .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + folderId));
        }

        String detectedContentType = detectMimeType(file);
        String storedFileName = computeSha256(file);

        var existing = fileMetadataRepository.findByStoredFileNameAndOwnerId(storedFileName, requesterId);
        if (existing.isPresent()) {
            log.info("Duplicate upload for user '{}' — returning existing metadata '{}'",
                    requesterId, existing.get().getId());
            return FileMetadataResponse.from(existing.get());
        }

        fileStorageService.store(file, requesterId, storedFileName);

        FileMetadata metadata = FileMetadata.builder()
                .originalFileName(sanitizeFileName(file.getOriginalFilename()))
                .storedFileName(storedFileName)
                .contentType(detectedContentType)
                .size(file.getSize())
                .owner(owner)
                .folder(folder)
                .build();

        try {
            FileMetadata saved = fileMetadataRepository.save(metadata);
            log.info("Uploaded file '{}' ('{}') for user '{}'",
                    saved.getId(), saved.getOriginalFileName(), requesterId);
            return FileMetadataResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            return fileMetadataRepository
                    .findByStoredFileNameAndOwnerId(storedFileName, requesterId)
                    .map(FileMetadataResponse::from)
                    .orElseThrow(() -> new FileStorageException("Unexpected conflict saving file metadata", e));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(metadata.isTrashed()) {
            throw new IllegalStateException("Cannot download a trashed file — restore it first");
        }
        return fileStorageService.load(requesterId, metadata.getStoredFileName());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileMetadataResponse> listFiles(UUID requesterId, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndTrashedFalse(requesterId, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileMetadataResponse> listTrashedFiles(UUID requesterId, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndTrashedTrue(requesterId, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    public PageResponse<FileMetadataResponse> listStarredFiles(UUID requesterId, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndStarredTrueAndTrashedFalse(requesterId, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    public PageResponse<FileMetadataResponse> searchFiles(UUID requesterId, String query, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(requesterId, query, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    public PageResponse<FileMetadataResponse> searchTrashedFiles(UUID requesterId, String query, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndTrashedTrueAndOriginalFileNameContainingIgnoreCase(requesterId, query, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    public PageResponse<FileMetadataResponse> searchStarredFiles(UUID requesterId, String query, Pageable pageable) {
        return PageResponse.from(
                fileMetadataRepository
                        .findByOwnerIdAndStarredTrueAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(requesterId, query, pageable)
                        .map(FileMetadataResponse::from)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FileMetadataResponse getFileMetadata(UUID fileId, UUID requesterId) {
        return FileMetadataResponse.from(getMetadataAndVerifyOwner(fileId, requesterId));
    }

    @Override
    @Transactional
    public FileMetadataResponse trashFile(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(metadata.isTrashed()) {
            throw new IllegalStateException("File is already in trash");
        }
        metadata.setTrashed(true);
        metadata.setTrashedAt(LocalDateTime.now());
        return FileMetadataResponse.from(metadata);
    }

    @Override
    @Transactional
    public FileMetadataResponse restore(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(!metadata.isTrashed()) {
            throw new IllegalStateException("File is not in trash");
        }
        metadata.setTrashed(false);
        metadata.setTrashedAt(null);
        return FileMetadataResponse.from(metadata);
    }

    @Override
    public FileMetadataResponse toggleStar(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if (metadata.isTrashed()) {
            throw new IllegalStateException("Cannot star a trashed file");
        }
        metadata.setStarred(!metadata.isStarred());
        fileMetadataRepository.save(metadata);
        return FileMetadataResponse.from(metadata);
    }

    @Override
    @Transactional
    public void deletePermanently(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(!metadata.isTrashed()) {
            throw new IllegalStateException("File must be in trash before deleting");
        }
        fileStorageService.delete(requesterId, metadata.getStoredFileName());
        fileMetadataRepository.delete(metadata);
        log.info("Permanently deleted file '{}' for user '{}'", fileId, requesterId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getStorageUsed(UUID requesterId) {
        return fileMetadataRepository.sumSizeByOwnerIdAndTrashedFalse(requesterId);
    }

    @Override
    public long getStarredCount(UUID requesterId) {
        return fileMetadataRepository.countByOwnerIdAndStarredTrueAndTrashedFalse(requesterId);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(UUID requesterId) {
        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + requesterId));

        long storageUsed = fileMetadataRepository.sumSizeByOwnerIdAndTrashedFalse(requesterId);
        long totalFiles  = fileMetadataRepository.countByOwnerIdAndTrashedFalse(requesterId);

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long filesThisWeek = fileMetadataRepository
                .countByOwnerIdAndTrashedFalseAndUploadedAtAfter(requesterId, weekAgo);
        long starredCount = fileMetadataRepository.countByOwnerIdAndStarredTrueAndTrashedFalse(requesterId);

        List<StorageBreakdownItem> breakdown = buildBreakdown(
                fileMetadataRepository.sumSizeByContentTypeForOwner(requesterId)
        );

        Pageable top5ByTrashedAt = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "trashedAt"));
        List<FileMetadataResponse> recentlyTrashed = fileMetadataRepository
                .findByOwnerIdAndTrashedTrue(requesterId, top5ByTrashedAt)
                .map(FileMetadataResponse::from)
                .getContent();

        return new DashboardStatsResponse(
                storageUsed,
                user.getStorageQuotaBytes(),
                totalFiles,
                filesThisWeek,
                starredCount,
                breakdown,
                recentlyTrashed
        );
    }

    @Override
    public FileMetadataResponse moveToFolder(UUID fileId, UUID targetFolderId, UUID requesterId) {
        FileMetadata file = getMetadataAndVerifyOwner(fileId, requesterId);

        if (file.isTrashed()) {
            throw new IllegalStateException("Cannot move a trashed file — restore it first");
        }

        if (targetFolderId == null) {
            // Move to root
            file.setFolder(null);
        } else {
            Folder targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new FolderNotFoundException("Folder not found: " + targetFolderId));

            if (!targetFolder.getOwner().getId().equals(requesterId)) {
                throw new FolderNotFoundException("Folder not found: " + targetFolderId);
            }

            if (targetFolder.isTrashed()) {
                throw new IllegalStateException("Cannot move a file into a trashed folder");
            }

            file.setFolder(targetFolder);
        }

        return FileMetadataResponse.from(fileMetadataRepository.save(file));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadAsZip(List<UUID> fileIds, UUID requesterId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos)) {
                Map<String, Integer> nameCount = new HashMap<>();
                for (UUID fileId : fileIds) {
                    FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
                    if (metadata.isTrashed()) continue;

                    Resource resource = fileStorageService.load(requesterId, metadata.getStoredFileName());
                    String name = metadata.getOriginalFileName();

                    // deduplicate names: file.txt → file(1).txt
                    if (nameCount.containsKey(name)) {
                        int count = nameCount.get(name) + 1;
                        nameCount.put(name, count);
                        int dot = name.lastIndexOf('.');
                        name = dot >= 0
                                ? name.substring(0, dot) + "(" + count + ")" + name.substring(dot)
                                : name + "(" + count + ")";
                    } else {
                        nameCount.put(name, 0);
                    }

                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream is = resource.getInputStream()) {
                        is.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
            return new ByteArrayResource(baos.toByteArray());
        } catch (IOException e) {
            throw new FileStorageException("Failed to create zip archive", e);
        }
    }

    // HELPER METHODS

    private List<StorageBreakdownItem> buildBreakdown(List<Object[]> rows) {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("Documents", 0L);
        totals.put("Images",    0L);
        totals.put("Videos",    0L);
        totals.put("Archives",  0L);
        totals.put("Other",     0L);

        for (Object[] row : rows) {
            String contentType = (String) row[0];
            long   bytes       = (Long)   row[1];
            totals.merge(categorize(contentType), bytes, Long::sum);
        }

        return totals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new StorageBreakdownItem(e.getKey(), e.getValue()))
                .toList();
    }

    private String categorize(String contentType) {
        if (contentType == null) return "Other";
        if (contentType.startsWith("image/"))  return "Images";
        if (contentType.startsWith("video/"))  return "Videos";
        if (contentType.startsWith("audio/"))  return "Videos"; // bucket into Videos or keep separate
        if (contentType.startsWith("text/"))   return "Documents";
        if (contentType.contains("pdf")
                || contentType.contains("word")
                || contentType.contains("document")
                || contentType.contains("spreadsheet")
                || contentType.contains("presentation")
                || contentType.contains("excel"))       return "Documents";
        if (contentType.contains("zip")
                || contentType.contains("tar")
                || contentType.contains("gzip")
                || contentType.contains("7z")
                || contentType.contains("rar")
                || contentType.contains("compressed"))  return "Archives";
        return "Other";
    }


    private FileMetadata getMetadataAndVerifyOwner(UUID fileId, UUID requesterId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));
        if (!metadata.getOwner().getId().equals(requesterId)) {
            throw new FileNotFoundException("File not found: " + fileId); // don't reveal existence
        }
        return metadata;
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {}
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new FileStorageException("Failed to compute file hash", e);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unnamed";
        return Paths.get(fileName).getFileName().toString();
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String detected = tika.detect(is, file.getOriginalFilename());
            if ("application/octet-stream".equals(detected)) {
                log.debug("Could not detect specific MIME type for '{}', using octet-stream",
                        file.getOriginalFilename());
            }
            return detected;
        } catch (IOException e) {
            throw new FileStorageException("Failed to detect file type", e);
        }
    }
}
