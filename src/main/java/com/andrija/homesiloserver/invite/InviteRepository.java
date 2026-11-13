package com.andrija.homesiloserver.invite;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    @EntityGraph(attributePaths = {"createdBy", "usedBy"})
    List<Invite> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "createdBy")
    Optional<Invite> findByTokenHash(String tokenHash);

    /**
     * Marks an invite used if it still can be. The row lock makes this the one
     * place two people using the same link at once are told apart: only one
     * update succeeds.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Invite i SET i.usedAt = :now
            WHERE i.tokenHash = :tokenHash AND i.usedAt IS NULL AND i.expiresAt > :now
            """)
    int claim(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(i) FROM Invite i WHERE i.usedAt IS NULL AND i.expiresAt > :now")
    long countPending(@Param("now") LocalDateTime now);

    /** Used and expired invites stay listed for a while, then go. */
    @Modifying
    @Query("DELETE FROM Invite i WHERE (i.usedAt IS NOT NULL AND i.usedAt < :before) OR i.expiresAt < :before")
    int deleteFinishedBefore(@Param("before") LocalDateTime before);
}
