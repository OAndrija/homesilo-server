package com.andrija.homesiloserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_metadata", indexes = {
        @Index(name = "idx_file_metadata_owner_id", columnList = "owner_id")
})
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

    //Original name of the user's file
    @Column(nullable = false)
    private String originalFileName;

    //The name that's saved in the File System, hash filename for deduplication functionality
    @Column(nullable = false, unique = true)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    //flag for the thrash bin feature
    @Column(nullable = false)
    @Builder.Default
    private boolean trashed = false;

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
    protected void onCreate(){
        LocalDateTime now = LocalDateTime.now();
        this.uploadedAt = now;
        this.lastModified = now;
    }

    @PreUpdate
    protected void onUpdate(){
        this.lastModified = LocalDateTime.now();
    }
}
