package com.andrija.homesiloserver.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A session that was signed out, kept until its token would have expired anyway. */
@Entity
@Table(name = "revoked_sessions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedSession {

    /** The token's {@code jti}. */
    @Id
    @Column(name = "token_id", nullable = false, updatable = false)
    private UUID tokenId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
