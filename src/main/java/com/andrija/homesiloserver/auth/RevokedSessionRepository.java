package com.andrija.homesiloserver.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

public interface RevokedSessionRepository extends JpaRepository<RevokedSession, UUID> {

    /** Their tokens have expired, so they'd be refused anyway. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedSession r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
