package com.andrija.homesiloserver.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    /** Ownership-scoped lookup: another user's file is indistinguishable from a missing one. */
    Optional<FileMetadata> findByIdAndOwnerId(UUID id, UUID ownerId);

    // ── Listings ─────────────────────────────────────────────────────────────

    Page<FileMetadata> findByOwnerIdAndTrashedFalse(UUID ownerId, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndStarredTrueAndTrashedFalse(UUID ownerId, Pageable pageable);

    /** Active files whose type starts with the prefix, e.g. {@code image/} for photos. */
    Page<FileMetadata> findByOwnerIdAndTrashedFalseAndContentTypeStartingWith(
            UUID ownerId, String contentTypePrefix, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndFolderIdAndTrashedFalse(UUID ownerId, UUID folderId, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndFolderIsNullAndTrashedFalse(UUID ownerId, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
            UUID ownerId, String query, Pageable pageable);

    Page<FileMetadata> findByOwnerIdAndStarredTrueAndTrashedFalseAndOriginalFileNameContainingIgnoreCase(
            UUID ownerId, String query, Pageable pageable);

    /** Trashed files whose folder is not itself trashed — what the Trash view shows. */
    @Query("SELECT f FROM FileMetadata f LEFT JOIN f.folder fo " +
            "WHERE f.owner.id = :ownerId AND f.trashed = true AND (fo IS NULL OR fo.trashed = false)")
    Page<FileMetadata> findTopLevelTrashed(@Param("ownerId") UUID ownerId, Pageable pageable);

    /** {@code pattern} must already be a LIKE pattern with wildcards escaped by {@code \}. */
    @Query("SELECT f FROM FileMetadata f LEFT JOIN f.folder fo " +
            "WHERE f.owner.id = :ownerId AND f.trashed = true AND (fo IS NULL OR fo.trashed = false) " +
            "AND LOWER(f.originalFileName) LIKE :pattern ESCAPE '\\'")
    Page<FileMetadata> findTopLevelTrashedByName(
            @Param("ownerId") UUID ownerId, @Param("pattern") String pattern, Pageable pageable);

    // ── Upload idempotency ───────────────────────────────────────────────────

    Optional<FileMetadata> findFirstByOwnerIdAndFolderIsNullAndOriginalFileNameAndStoredFileNameAndTrashedFalse(
            UUID ownerId, String originalFileName, String storedFileName);

    Optional<FileMetadata> findFirstByOwnerIdAndFolderIdAndOriginalFileNameAndStoredFileNameAndTrashedFalse(
            UUID ownerId, UUID folderId, String originalFileName, String storedFileName);

    boolean existsByOwnerIdAndStoredFileName(UUID ownerId, String storedFileName);

    // ── Archives ─────────────────────────────────────────────────────────────

    List<FileMetadata> findByOwnerIdAndIdInAndTrashedFalse(UUID ownerId, Collection<UUID> ids);

    List<FileMetadata> findByOwnerIdAndFolderIdInAndTrashedFalse(UUID ownerId, Collection<UUID> folderIds);

    // ── Stats ────────────────────────────────────────────────────────────────

    long countByOwnerIdAndTrashedFalse(UUID ownerId);

    long countByOwnerIdAndStarredTrueAndTrashedFalse(UUID ownerId);

    long countByOwnerIdAndTrashedFalseAndUploadedAtAfter(UUID ownerId, LocalDateTime since);

    /** Everything the user stores, trash included — trashed files still occupy disk until deleted. */
    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner.id = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

    // ── Admin: usage across accounts ─────────────────────────────────────────

    /** Storage (trash included) and active file count for each of the given owners that has files. */
    @Query("SELECT f.owner.id AS ownerId, COALESCE(SUM(f.size), 0) AS bytes, "
            + "SUM(CASE WHEN f.trashed = false THEN 1 ELSE 0 END) AS files "
            + "FROM FileMetadata f WHERE f.owner.id IN :ownerIds GROUP BY f.owner.id")
    List<OwnerUsage> usageByOwners(@Param("ownerIds") Collection<UUID> ownerIds);

    interface OwnerUsage {
        UUID getOwnerId();

        long getBytes();

        long getFiles();
    }

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f")
    long sumAllSizes();

    /** Accounts using the most storage (trash included), largest first. */
    @Query("SELECT f.owner.id AS ownerId, f.owner.username AS username, SUM(f.size) AS bytes "
            + "FROM FileMetadata f GROUP BY f.owner.id, f.owner.username ORDER BY SUM(f.size) DESC")
    List<TopUsage> topUsage(Pageable pageable);

    interface TopUsage {
        UUID getOwnerId();

        String getUsername();

        long getBytes();
    }

    long countByTrashedFalse();

    @Query("SELECT DISTINCT f.storedFileName FROM FileMetadata f WHERE f.owner.id = :ownerId")
    List<String> findBlobsByOwner(@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FileMetadata f WHERE f.owner.id = :ownerId")
    int deleteAllByOwner(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = true")
    long sumTrashedSizeByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT f.contentType AS contentType, SUM(f.size) AS bytes FROM FileMetadata f " +
            "WHERE f.owner.id = :ownerId AND f.trashed = false GROUP BY f.contentType")
    List<ContentTypeUsage> sumActiveSizeByContentType(@Param("ownerId") UUID ownerId);

    interface ContentTypeUsage {
        String getContentType();

        Long getBytes();
    }

    // ── Bulk trash / restore for folder cascades ─────────────────────────────

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FileMetadata f SET f.trashed = true, f.trashedAt = :now " +
            "WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds AND f.trashed = false")
    int markTrashedInFolders(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") Collection<UUID> folderIds,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FileMetadata f SET f.trashed = false, f.trashedAt = null " +
            "WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds AND f.trashed = true")
    int markRestoredInFolders(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") Collection<UUID> folderIds);

    /** How many files the folders hold between them, and their total size. */
    @Query("SELECT COUNT(f) AS files, COALESCE(SUM(f.size), 0) AS bytes FROM FileMetadata f " +
            "WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds AND f.trashed = :trashed")
    FolderUsage usageInFolders(
            @Param("ownerId") UUID ownerId,
            @Param("folderIds") Collection<UUID> folderIds,
            @Param("trashed") boolean trashed);

    interface FolderUsage {
        long getFiles();

        long getBytes();
    }

    // ── Permanent deletion ───────────────────────────────────────────────────

    @Query("SELECT DISTINCT f.storedFileName FROM FileMetadata f " +
            "WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds")
    List<String> findBlobsInFolders(@Param("ownerId") UUID ownerId, @Param("folderIds") Collection<UUID> folderIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.folder.id IN :folderIds")
    int deleteInFolders(@Param("ownerId") UUID ownerId, @Param("folderIds") Collection<UUID> folderIds);

    @Query("SELECT DISTINCT f.storedFileName FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = true")
    List<String> findTrashedBlobs(@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.trashed = true")
    int deleteTrashed(@Param("ownerId") UUID ownerId);

    List<FileMetadata> findByTrashedTrueAndTrashedAtBefore(LocalDateTime cutoff);
}
