package com.andrija.homesiloserver.file;

import com.andrija.homesiloserver.folder.Folder;
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
 * A file entry as the user sees it. The bytes live in the blob store under
 * {@link #storedFileName} (the SHA-256 of the content); several entries may
 * share one blob.
 */
@Entity
@Table(
        name = "file_metadata",
        indexes = {
                @Index(name = "idx_file_metadata_owner_id", columnList = "owner_id"),
                @Index(name = "idx_file_metadata_trashed_at", columnList = "trashed_at"),
                @Index(name = "idx_file_metadata_folder_id", columnList = "folder_id"),
                @Index(name = "idx_file_metadata_owner_stored_file", columnList = "owner_id, stored_file_name")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String originalFileName;

    /** SHA-256 (hex) of the content; the blob's key in storage. */
    @Column(nullable = false, updatable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    @Builder.Default
    private boolean trashed = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean starred = false;

    @Column
    private LocalDateTime trashedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    /** {@code null} for files at the top level. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.uploadedAt = now;
        this.lastModified = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModified = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMetadata other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
