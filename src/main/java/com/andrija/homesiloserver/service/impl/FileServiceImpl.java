package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.dto.PageResponse;
import com.andrija.homesiloserver.entity.FileMetadata;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.exception.FileNotFoundException;
import com.andrija.homesiloserver.exception.FileStorageException;
import com.andrija.homesiloserver.exception.UserNotFoundException;
import com.andrija.homesiloserver.repository.FileMetadataRepository;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.service.FileService;
import com.andrija.homesiloserver.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class FileServiceImpl implements FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @Override
    public FileMetadataResponse upload(MultipartFile file, UUID requesterId) {
        User owner =  userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found " + requesterId));

        String storedFileName = computeSha256(file);

        var existing = fileMetadataRepository.findByStoredFileNameAndOwnerId(storedFileName, requesterId);
        if(existing.isPresent()) {
            log.info("Duplicate upload for user '{}' — returning existing metadata '{}'",
                    requesterId, existing.get().getId());
            return FileMetadataResponse.from(existing.get());
        }

        fileStorageService.store(file, requesterId, storedFileName);

        FileMetadata metadata = FileMetadata.builder()
                .originalFileName(sanitizeFileName(file.getOriginalFilename()))
                .storedFileName(storedFileName)
                .contentType(file.getContentType())
                .size(file.getSize())
                .owner(owner)
                .build();

        try {
            FileMetadata saved = fileMetadataRepository.save(metadata);
            log.info("Uploaded file '{}' ('{}') for user '{}'",
                    saved.getId(), saved.getOriginalFileName(), requesterId);
            return FileMetadataResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent identical upload won — return that record
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
    public FileMetadataResponse getFileMetadata(UUID fileId, UUID requesterId) {
        return FileMetadataResponse.from(getMetadataAndVerifyOwner(fileId, requesterId));
    }

    @Override
    public FileMetadataResponse trashFile(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(metadata.isTrashed()) {
            throw new IllegalStateException("File is already in trash");
        }
        metadata.setTrashed(true);
        return FileMetadataResponse.from(metadata);
    }

    @Override
    public FileMetadataResponse restore(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(!metadata.isTrashed()) {
            throw new IllegalStateException("File is not in trash");
        }
        metadata.setTrashed(false);
        return FileMetadataResponse.from(metadata);
    }

    @Override
    public void deletePermanently(UUID fileId, UUID requesterId) {
        FileMetadata metadata = getMetadataAndVerifyOwner(fileId, requesterId);
        if(!metadata.isTrashed()) {
            throw new IllegalStateException("File must be in trash before deleting");
        }
        fileStorageService.delete(requesterId, metadata.getStoredFileName());
        fileMetadataRepository.delete(metadata);
        log.info("Permanently deleted file '{}' for user '{}'", fileId, requesterId);
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
}
