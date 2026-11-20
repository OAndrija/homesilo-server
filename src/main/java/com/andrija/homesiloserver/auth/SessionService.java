package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Ending sessions on the server. A session token is signed, not stored, so
 * clearing the cookie alone would leave a copied token working until it
 * expires; signing out records the session as ended instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final JwtService jwtService;
    private final RevokedSessionRepository revokedSessions;
    private final UserService userService;
    private final ActivityRecorder activity;

    /** Ends the session this token belongs to (sign out). Unreadable or expired tokens need nothing. */
    @Transactional
    public void end(String token) {
        if (token == null) return;
        jwtService.parse(token)
                .filter(claims -> claims.tokenId() != null && claims.expiresAt() != null)
                .ifPresent(claims -> revokedSessions.save(new RevokedSession(claims.tokenId(),
                        LocalDateTime.ofInstant(claims.expiresAt(), ZoneId.systemDefault()))));
    }

    /**
     * Ends every session of this account started before now. The caller then
     * issues this device a new session, so only the other devices are signed out.
     */
    @Transactional
    public User endOtherSessions(UUID userId) {
        User user = userService.getEntity(userId);
        user.setSessionsRevokedAt(LocalDateTime.now());
        log.info("User '{}' signed out their other sessions", userId);
        activity.recordAs(user, ActivityAction.SESSIONS_REVOKED);
        return user;
    }

    /** Nightly: ended sessions whose tokens have expired by now don't need remembering. */
    @Transactional
    public int forgetExpired() {
        return revokedSessions.deleteExpired(LocalDateTime.now());
    }
}
