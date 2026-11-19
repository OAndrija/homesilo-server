package com.andrija.homesiloserver.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    Optional<UploadSession> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    /** Bytes promised by unfinished uploads, counted against the quota so they can't fill the disk. */
    @Query("SELECT COALESCE(SUM(s.size), 0) FROM UploadSession s WHERE s.ownerId = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

    List<UploadSession> findByUpdatedAtBefore(LocalDateTime before);

    @Query(value = "SELECT chunk_index FROM upload_session_chunks WHERE session_id = :id ORDER BY chunk_index",
            nativeQuery = true)
    List<Integer> receivedChunks(@Param("id") UUID sessionId);

    @Query(value = "SELECT COUNT(*) FROM upload_session_chunks WHERE session_id = :id", nativeQuery = true)
    long countReceivedChunks(@Param("id") UUID sessionId);

    /** Records that a chunk arrived; sending it again (a retry) changes nothing. */
    @Modifying
    @Query(value = """
            INSERT INTO upload_session_chunks (session_id, chunk_index) VALUES (:id, :index)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void recordChunk(@Param("id") UUID sessionId, @Param("index") int index);

    @Modifying
    @Query("UPDATE UploadSession s SET s.updatedAt = :now WHERE s.id = :id")
    void touch(@Param("id") UUID sessionId, @Param("now") LocalDateTime now);
}
