package com.andrija.homesiloserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "folder",
        indexes = {
                @Index(name = "idx_folder_owner_id",  columnList = "owner_id"),
                @Index(name = "idx_folder_parent_id", columnList = "parent_id")
        }
        // Note: unique constraint on (owner_id, parent_id, name) is enforced in the
        // service layer rather than as a DB constraint, because most databases treat
        // NULL as distinct in unique constraints — meaning two root-level folders
        // (parent_id IS NULL) with the same name would pass a DB-level constraint.
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // null = root-level folder; non-null = nested inside another folder
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true)
    private Folder parent;

    @Column(nullable = false)
    @Builder.Default
    private boolean trashed = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean starred = false;

    @Column
    private LocalDateTime trashedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastModified = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModified = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Folder that = (Folder) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}