package com.andrija.homesiloserver.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A file being uploaded in chunks. */
@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UploadSession {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    /** Where the file goes; null for the top of My Files. */
    private UUID folderId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private int chunkSize;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last time a chunk arrived; idle sessions are cleared out after a day. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** How many bytes chunk {@code index} must have: all full-size except possibly the last. */
    public long expectedLength(int index) {
        long offset = (long) index * chunkSize;
        return Math.min(chunkSize, size - offset);
    }
}
