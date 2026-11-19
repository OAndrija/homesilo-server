package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and verifies the HMAC-signed session tokens. */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(HomesiloProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.jwt().expiration();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /** Returns the verified claims, or empty if the token is malformed, tampered with or expired. */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null || claims.getIssuedAt() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    // Tokens from before session ids existed have none; they can't be signed out one by one.
                    claims.getId() != null ? UUID.fromString(claims.getId()) : null,
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration() != null ? claims.getExpiration().toInstant() : null
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected session token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Duration expiration() {
        return expiration;
    }

    /**
     * @param tokenId   the session's id ({@code jti}), null on tokens issued before ids existed
     * @param expiresAt when the token stops working on its own
     */
    public record TokenClaims(UUID userId, UUID tokenId, Instant issuedAt, Instant expiresAt) {}
}
