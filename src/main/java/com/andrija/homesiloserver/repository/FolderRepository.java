package com.andrija.homesiloserver.repository;

import com.andrija.homesiloserver.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    // All folders of a user
    List<Folder> findByOwnerIdAndTrashedFalse(UUID ownerId);
    List<Folder> findByOwnerIdAndTrashedTrue(UUID ownerId);

    // Children of a specific folder
    List<Folder> findByOwnerIdAndParentIdAndTrashedFalseOrderByNameAsc(UUID ownerId, UUID parentId);
    List<Folder> findByOwnerIdAndParentIdAndTrashedTrueOrderByNameAsc(UUID ownerId, UUID parentId);

    // Root level folders
    List<Folder> findByOwnerIdAndParentIsNullAndTrashedFalseOrderByNameAsc(UUID ownerId);
    List<Folder> findByOwnerIdAndParentIsNullAndTrashedTrueOrderByNameAsc(UUID ownerId);

    // Name uniqueness checks (enforced in service layer, not DB constraint)
    boolean existsByOwnerIdAndParentIsNullAndNameAndTrashedFalse(UUID ownerId, String name);
    boolean existsByOwnerIdAndParentIdAndNameAndTrashedFalse(UUID ownerId, UUID parentId, String name);

    // Same checks excluding a specific folder (used during rename)
    boolean existsByOwnerIdAndParentIsNullAndNameAndTrashedFalseAndIdNot(UUID ownerId, String name, UUID excludeId);
    boolean existsByOwnerIdAndParentIdAndNameAndTrashedFalseAndIdNot(UUID ownerId, UUID parentId, String name, UUID excludeId);

    // Trashed folder lookup
    Optional<Folder> findByIdAndOwnerId(UUID id, UUID ownerId);

    // Count direct children (used to check if folder is empty)
    long countByParentIdAndTrashedFalse(UUID parentId);

    // All descendants via parent chain (for BFS in service layer)
    @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId AND f.trashed = false AND f.parent.id IN :parentIds")
    List<Folder> findByOwnerIdAndParentIdInAndTrashedFalse(
            @Param("ownerId") UUID ownerId,
            @Param("parentIds") List<UUID> parentIds
    );

    @Query("SELECT f FROM Folder f WHERE f.owner.id = :ownerId AND f.trashed = true AND f.parent.id IN :parentIds")
    List<Folder> findByOwnerIdAndParentIdInAndTrashedTrue(
            @Param("ownerId") UUID ownerId,
            @Param("parentIds") List<UUID> parentIds
    );
}
