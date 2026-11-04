package com.andrija.homesiloserver.folder;

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

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    /** Ownership-scoped lookup: another user's folder is indistinguishable from a missing one. */
    Optional<Folder> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Folder> findByOwnerIdAndParentIdAndTrashedFalseOrderByNameAsc(UUID ownerId, UUID parentId);

    List<Folder> findByOwnerIdAndParentIsNullAndTrashedFalseOrderByNameAsc(UUID ownerId);

    List<Folder> findByOwnerIdAndTrashedFalseAndNameContainingIgnoreCaseOrderByNameAsc(UUID ownerId, String query);

    // Sibling-name checks (see Folder javadoc for why this isn't a DB constraint)
    boolean existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndTrashedFalse(UUID ownerId, String name);

    boolean existsByOwnerIdAndParentIdAndNameIgnoreCaseAndTrashedFalse(UUID ownerId, UUID parentId, String name);

    boolean existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndTrashedFalseAndIdNot(UUID ownerId, String name, UUID excludeId);

    boolean existsByOwnerIdAndParentIdAndNameIgnoreCaseAndTrashedFalseAndIdNot(UUID ownerId, UUID parentId, String name, UUID excludeId);

    /** One BFS level of the tree. */
    @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId AND f.trashed = :trashed AND f.parent.id IN :parentIds")
    List<Folder> findChildren(
            @Param("ownerId") UUID ownerId,
            @Param("parentIds") Collection<UUID> parentIds,
            @Param("trashed") boolean trashed);

    /** Trashed folders whose parent is not trashed — what the Trash view shows. */
    @Query("SELECT f FROM Folder f LEFT JOIN f.parent p " +
            "WHERE f.owner.id = :ownerId AND f.trashed = true AND (p IS NULL OR p.trashed = false)")
    Page<Folder> findTopLevelTrashed(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT f FROM Folder f LEFT JOIN f.parent p " +
            "WHERE f.trashed = true AND f.trashedAt < :cutoff AND (p IS NULL OR p.trashed = false)")
    List<Folder> findTopLevelTrashedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT f.id FROM Folder f WHERE f.owner.id = :ownerId AND f.trashed = true")
    List<UUID> findTrashedIds(@Param("ownerId") UUID ownerId);

    long countByOwnerIdAndTrashedFalse(UUID ownerId);

    // ── Bulk operations (bypass the persistence context) ─────────────────────

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Folder f SET f.trashed = true, f.trashedAt = :now, f.lastModified = :now " +
            "WHERE f.owner.id = :ownerId AND f.id IN :ids")
    int markTrashed(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Folder f SET f.trashed = false, f.trashedAt = null, f.lastModified = :now " +
            "WHERE f.owner.id = :ownerId AND f.id IN :ids")
    int markRestored(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    /** Breaks parent links first so the folders can be deleted in a single statement regardless of depth. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Folder f SET f.parent = null WHERE f.owner.id = :ownerId AND f.id IN :ids")
    int detachFromParents(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Folder f WHERE f.owner.id = :ownerId AND f.id IN :ids")
    int deleteByIds(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);
}
