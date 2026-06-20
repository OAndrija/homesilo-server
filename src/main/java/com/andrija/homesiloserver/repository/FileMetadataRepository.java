package com.andrija.homesiloserver.repository;

import com.andrija.homesiloserver.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findByOwnerIdAndTrashedFalse(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndTrashedTrue(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(UUID ownerId, String query, Pageable pageable);
    Optional<FileMetadata> findByStoredFileNameAndOwnerId(String storedFileName, UUID userId);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = false")
    long sumSizeByOwnerIdAndTrashedFalse(@Param("ownerId") UUID ownerId);

    long countByOwnerId(UUID ownerId);
}
