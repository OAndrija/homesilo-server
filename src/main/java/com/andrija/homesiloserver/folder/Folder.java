package com.andrija.homesiloserver.folder;

import com.andrija.homesiloserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Folder tree stored as an adjacency list ({@code parent_id}). Name uniqueness
 * among active siblings is enforced in {@link FolderService}: a plain unique
 * constraint can't express it because NULL parents compare as distinct and
 * trashed folders may share names.
 */
@Entity
@Table(
        name = "folder",
        indexes = {
                @Index(name = "idx_folder_owner_id", columnList = "owner_id"),
                @Index(name = "idx_folder_parent_id", columnList = "parent_id"),
                @Index(name = "idx_folder_trashed_at", columnList = "trashed_at")
        }
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
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    /** {@code null} for a top-level folder. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;

    @Column(nullable = false)
    @Builder.Default
    private boolean trashed = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean starred = false;

    @Column
    private LocalDateTime trashedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    public UUID getParentId() {
        return parent != null ? parent.getId() : null;
    }

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
        if (!(o instanceof Folder other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
