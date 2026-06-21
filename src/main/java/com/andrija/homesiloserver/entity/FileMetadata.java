package com.andrija.homesiloserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "file_metadata",
        indexes = {
                @Index(name = "idx_file_metadata_owner_id", columnList = "owner_id"),
                @Index(name = "idx_file_metadata_trashed_at", columnList = "trashed_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_file_metadata_owner_stored_filename",
                        columnNames = {"owner_id", "stored_file_name"}
                )
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

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    @Builder.Default
    private boolean trashed = false;

    @Column
    private boolean starred = false;

    @Column
    private LocalDateTime trashedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

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
}
