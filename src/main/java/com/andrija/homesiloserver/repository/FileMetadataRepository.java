package com.andrija.homesiloserver.repository;

import com.andrija.homesiloserver.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    Page<FileMetadata> findByOwnerIdAndTrashedFalse(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndTrashedTrue(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndStarredTrueAndTrashedFalse(UUID ownerId, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
            UUID ownerId, String query, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndTrashedTrueAndOriginalFileNameContainingIgnoreCase(
            UUID ownerId, String query, Pageable pageable);
    Page<FileMetadata> findByOwnerIdAndStarredTrueAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
            UUID ownerId, String query, Pageable pageable);
    Optional<FileMetadata> findByStoredFileNameAndOwnerId(String storedFileName, UUID userId);

    long countByOwnerIdAndTrashedFalse(UUID ownerId);
    long countByOwnerIdAndStarredTrueAndTrashedFalse(UUID ownerId);
    long countByOwnerIdAndTrashedFalseAndUploadedAtAfter(UUID ownerId, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = false")
    long sumSizeByOwnerIdAndTrashedFalse(@Param("ownerId") UUID ownerId);

    @Query("SELECT f.contentType, SUM(f.size) FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = false GROUP BY f.contentType")
    List<Object[]> sumSizeByContentTypeForOwner(@Param("ownerId") UUID ownerId);

    Page<FileMetadata> findByOwnerIdAndFolderIdAndTrashedFalse(UUID ownerId, UUID folderId, Pageable pageable);

    // Files at root level (no folder)
    Page<FileMetadata> findByOwnerIdAndFolderIsNullAndTrashedFalse(UUID ownerId, Pageable pageable);

    // All files in a set of folder IDs — used for BFS cascade operations
    List<FileMetadata> findByOwnerIdAndFolderIdInAndTrashedFalse(UUID ownerId, List<UUID> folderIds);
    List<FileMetadata> findByOwnerIdAndFolderIdInAndTrashedTrue(UUID ownerId, List<UUID> folderIds);

    // Direct files in a folder (non-paginated, for cascade trash/restore/delete)
    List<FileMetadata> findByOwnerIdAndFolderIdAndTrashedFalse(UUID ownerId, UUID folderId);
    List<FileMetadata> findByOwnerIdAndFolderIdAndTrashedTrue(UUID ownerId, UUID folderId);

    // Bulk folder-cascade operations

    @Modifying
    @Query("UPDATE FileMetadata f SET f.trashed = true, f.trashedAt = :now WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds AND f.trashed = false")
    int bulkTrashByOwnerIdAndFolderIds(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") List<UUID> folderIds,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("UPDATE FileMetadata f SET f.trashed = false, f.trashedAt = null WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds AND f.trashed = true")
    int bulkRestoreByOwnerIdAndFolderIds(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") List<UUID> folderIds
    );

    @Query("SELECT f FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds")
    List<FileMetadata> findAllByOwnerIdAndFolderIds(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") List<UUID> folderIds
    );

    // Top-level trashed files: trashed files whose parent folder is either
    // null (root) or not itself trashed. LEFT JOIN prevents the implicit
    // inner join from excluding null-folder rows.
    @Query("SELECT f FROM FileMetadata f LEFT JOIN f.folder fo " +
            "WHERE f.owner.id = :ownerId AND f.trashed = true " +
            "AND (f.folder IS NULL OR fo.trashed = false)")
    Page<FileMetadata> findTopLevelTrashedFiles(
            @Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT f FROM FileMetadata f LEFT JOIN f.folder fo " +
            "WHERE f.owner.id = :ownerId AND f.trashed = true " +
            "AND (f.folder IS NULL OR fo.trashed = false) " +
            "AND LOWER(f.originalFileName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<FileMetadata> findTopLevelTrashedFilesByName(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable);
}
