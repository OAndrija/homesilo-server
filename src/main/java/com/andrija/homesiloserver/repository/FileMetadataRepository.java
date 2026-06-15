package com.andrija.homesiloserver.repository;

import com.andrija.homesiloserver.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findByOwnerIdAndTrashedFalse(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndTrashedTrue(UUID ownerId, Pageable pageable);
    Optional<FileMetadata> findByStoredFileNameAndOwnerId(String storedFileName, UUID userId);

    long countByOwnerId(UUID ownerId);
}
